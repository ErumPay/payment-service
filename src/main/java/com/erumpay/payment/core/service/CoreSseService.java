package com.erumpay.payment.core.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CoreSseService {

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;
    private static final long REPLAY_CACHE_TTL_MS = 10L * 60L * 1000L;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Map<Long, ReplayEvent> latestEvents = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long paymentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(paymentId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(paymentId, emitter));
        emitter.onTimeout(() -> removeEmitter(paymentId, emitter));
        emitter.onError(error -> removeEmitter(paymentId, emitter));

        sendConnectedEvent(emitter, paymentId);
        replayLatestEventIfPresent(emitter, paymentId);
        return emitter;
    }

    private void sendConnectedEvent(SseEmitter emitter, Long paymentId) {
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .id(String.valueOf(paymentId))
                    .data("연결완료되었습니다"));

        } catch (IOException e) {
            log.warn("SSE initial event send failed. paymentId={}", paymentId, e);
            emitter.completeWithError(e);
            removeEmitter(paymentId, emitter);
        }
    }

    public void pushEvent(Long paymentId, String eventName, Object data) {
        cacheLatestEvent(paymentId, eventName, data);

        List<SseEmitter> emitterList = emitters.get(paymentId);
        if (emitterList == null || emitterList.isEmpty())
            return;

        for (SseEmitter emitter : new ArrayList<>(emitterList)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .id(String.valueOf(paymentId))
                        .data(data));
            } catch (IOException e) {
                emitter.completeWithError(e);
                removeEmitter(paymentId, emitter);
            }
        }
    }

    // [be] codex 260529 | 결제 완료 후 해당 paymentId에 연결된 SSE 구독을 종료한다.
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

    // [be] codex 260529 | SSE 구독 전에 발행된 최신 이벤트를 paymentId 기준으로 잠시 보관한다.
    private void cacheLatestEvent(Long paymentId, String eventName, Object data) {
        latestEvents.put(paymentId, new ReplayEvent(eventName, data, System.currentTimeMillis()));
    }

    // [be] codex 260529 | 구독 직후 캐시된 최신 이벤트를 1회 재전송해 /prepare 선발행 유실을 줄인다.
    private void replayLatestEventIfPresent(SseEmitter emitter, Long paymentId) {
        ReplayEvent replayEvent = latestEvents.get(paymentId);

        if (replayEvent == null) {
            return;
        }

        long ageMs = System.currentTimeMillis() - replayEvent.createdAtMs();
        if (ageMs > REPLAY_CACHE_TTL_MS) {
            latestEvents.remove(paymentId, replayEvent);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name(replayEvent.eventName())
                    .id(String.valueOf(paymentId))
                    .data(replayEvent.data()));
        } catch (IOException e) {
            log.warn("SSE replay event send failed. paymentId={}, eventName={}",
                    paymentId, replayEvent.eventName(), e);
            emitter.completeWithError(e);
            removeEmitter(paymentId, emitter);
        }
    }

    private record ReplayEvent(String eventName, Object data, long createdAtMs) {
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

}
