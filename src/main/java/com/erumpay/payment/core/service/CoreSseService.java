package com.erumpay.payment.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.erumpay.payment.core.domain.dto.CoreSseEventResponse;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.CoreSseRedisMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoreSseService {

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;
    private static final long RECOMMENDATION_CACHE_TTL_MS = 10L * 60L * 1000L;
    private static final String CONNECTED_EVENT_NAME = "connected";
    private static final String PAYMENT_UPDATED_EVENT_NAME = "payment-updated";
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final CoreSseTopicProperties coreSseTopicProperties;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, CachedRecommendationEvent> recommendationEvents = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long paymentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        log.info("subscribe called.");

        emitters.computeIfAbsent(paymentId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(paymentId, emitter));
        emitter.onTimeout(() -> removeEmitter(paymentId, emitter));
        emitter.onError(error -> removeEmitter(paymentId, emitter));

        sendConnectedEvent(emitter, paymentId);
        replayCachedRecommendationIfPresent(emitter, paymentId);
        return emitter;
    }

    private void sendConnectedEvent(SseEmitter emitter, Long paymentId) {
        CoreSseEventResponse connectedEvent = CoreSseEventResponse.of(
                CoreSseEventType.CONNECTED,
                paymentId,
                Map.of("message", "연결완료되었습니다"));

        try {
            emitter.send(SseEmitter.event()
                    .name(CONNECTED_EVENT_NAME)
                    .id(String.valueOf(paymentId))
                    .data(connectedEvent));

        } catch (IOException e) {
            log.warn("SSE initial event send failed. paymentId={}", paymentId, e);
            emitter.completeWithError(e);
            removeEmitter(paymentId, emitter);
        }
    }

    // [be] 다윤 260601 | 코어 SSE 전파 진입점: 상태 변경 이벤트를 Redis Pub/Sub로 모든 인스턴스에 전달한다.
    public void publishPaymentUpdated(Long paymentId, CoreSseEventType eventType, Object payload) {
        log.info("publishPaymentUpdated called.");
        CoreSseEventResponse event = CoreSseEventResponse.of(eventType, paymentId, payload);
        cacheRecommendationEventIfNeeded(paymentId, event);

        try {
            CoreSseRedisMessage message = CoreSseRedisMessage.of(paymentId, event);
            stringRedisTemplate.convertAndSend(
                    coreSseTopicProperties.getPaymentEvents(),
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Core SSE Redis publish failed. fallback to local emit. paymentId={}, eventType={}",
                    paymentId, eventType, e);
            applyPaymentUpdatedFromRedis(paymentId, event);
        }
    }

    // [be] 다윤 260601 | Redis에서 전달된 코어 SSE 이벤트를 현재 인스턴스의 로컬 SSE 연결에 전송한다.
    public void applyPaymentUpdatedFromRedis(Long paymentId, CoreSseEventResponse event) {
        log.info("applyPaymentUpdatedFromRedis called.");
        cacheRecommendationEventIfNeeded(paymentId, event);
        sendLocalPaymentUpdated(paymentId, event);

        if (shouldCompleteSubscriptions(event)) {
            completeSubscriptions(paymentId);
        }
    }

    // [be] 다윤 260601 | 현재 인스턴스에 연결된 구독자에게만 payment-updated 이벤트를 보낸다.
    public void sendLocalPaymentUpdated(Long paymentId, CoreSseEventResponse event) {
        log.info("sendLocalPaymentUpdated called.");
        List<SseEmitter> emitterList = emitters.get(paymentId);
        if (emitterList == null || emitterList.isEmpty())
            return;

        for (SseEmitter emitter : new ArrayList<>(emitterList)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(PAYMENT_UPDATED_EVENT_NAME)
                        .id(String.valueOf(paymentId))
                        .data(event));
            } catch (IOException e) {
                emitter.completeWithError(e);
                removeEmitter(paymentId, emitter);
            }
        }
    }

    // [be] 다윤 260529 | 결제 완료 후 해당 paymentId에 연결된 SSE 구독을 종료한다.
    public void completeSubscriptions(Long paymentId) {
        List<SseEmitter> emitterList = emitters.remove(paymentId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : new ArrayList<>(emitterList)) {
            try {
                emitter.complete();
            } catch (RuntimeException e) {
                log.warn("SSE complete failed. paymentId={}", paymentId, e);
            }
        }
    }

    private void cacheRecommendationEventIfNeeded(Long paymentId, CoreSseEventResponse event) {
        log.info("cacheRecommendationEventIfNeeded called.");

        if (paymentId == null || event == null || !isRecommendationEvent(event.getEventType())) {
            return;
        }

        recommendationEvents.put(paymentId, new CachedRecommendationEvent(event, System.currentTimeMillis()));
    }

    private void replayCachedRecommendationIfPresent(SseEmitter emitter, Long paymentId) {
        log.info("replayCachedRecommendationIfPresent called.");
        CachedRecommendationEvent cachedEvent = recommendationEvents.get(paymentId);

        if (cachedEvent == null) {
            return;
        }

        long ageMs = System.currentTimeMillis() - cachedEvent.createdAtMs();
        if (ageMs > RECOMMENDATION_CACHE_TTL_MS) {
            recommendationEvents.remove(paymentId, cachedEvent);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(PAYMENT_UPDATED_EVENT_NAME)
                    .id(String.valueOf(paymentId))
                    .data(cachedEvent.event()));
        } catch (IOException e) {
            log.warn("SSE recommendation replay send failed. paymentId={}", paymentId, e);
            emitter.completeWithError(e);
            removeEmitter(paymentId, emitter);
        }
    }

    private boolean isRecommendationEvent(CoreSseEventType eventType) {
        return eventType == CoreSseEventType.RECOMMENDATION_SUCCEEDED
                || eventType == CoreSseEventType.RECOMMENDATION_FAILED;
    }

    private boolean shouldCompleteSubscriptions(CoreSseEventResponse event) {
        return event != null && event.getEventType() == CoreSseEventType.PAYMENT_PAID;
    }

    private void removeEmitter(Long paymentId, SseEmitter emitter) {
        List<SseEmitter> emitterList = emitters.get(paymentId);
        if (emitterList == null) {
            return;
        }

        emitterList.remove(emitter);
        if (emitterList.isEmpty()) {
            emitters.remove(paymentId);
        }
    }

    private record CachedRecommendationEvent(CoreSseEventResponse event, long createdAtMs) {
    }

}
