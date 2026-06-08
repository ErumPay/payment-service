package com.erumpay.payment.remote.service;

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

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.dao.RemotePayRequestRepository;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePaySseEventResponse;
import com.erumpay.payment.remote.domain.dto.RemotePaySseRedisMessage;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemotePaySseService {

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;

    private final RemotePayRequestRepository remotePayRequestRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final RemotePaySseTopicProperties remotePaySseTopicProperties;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // [be] 영은 260528 1110 | 원격결제 요청자/대상자 권한을 확인한 뒤 request_id별 SSE 구독자를 등록한다.
    // [be] 영은 260528 1110 | 알림 클릭 후 화면을 열어둔 요청자에게 대상자의 결제/거절/만료 상태 변경을 즉시 전달하기 위한 연결이다.
    public SseEmitter subscribe(Long requestId, Long userId) {
        RemotePayCreateResponse request = getRequestForSse(requestId, userId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(requestId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(requestId, emitter));
        emitter.onTimeout(() -> removeEmitter(requestId, emitter));
        emitter.onError(error -> removeEmitter(requestId, emitter));

        sendEvent(emitter, requestId, "connected", RemotePaySseEventResponse.of("CONNECTED", request));
        return emitter;
    }

    // [be] 영은 260528 1110 | 상태 변경 사실만 Redis에 발행해 다중 Pod 환경에서도 해당 request를 보는 모든 화면을 갱신한다.
    public void publishRequestUpdated(Long requestId, String eventType, RemotePayCreateResponse request) {
        try {
            RemotePaySseRedisMessage message = RemotePaySseRedisMessage.of(requestId, eventType);
            stringRedisTemplate.convertAndSend(
                    remotePaySseTopicProperties.getRequestEvents(),
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("RemotePay SSE Redis publish failed. fallback to local emit. requestId={}, eventType={}",
                    requestId, eventType, e);
            sendLocalRequestUpdated(requestId, eventType, request);
        }
    }

    // [be] 영은 260528 1110 | Redis에서 수신한 상태 변경 이벤트를 현재 서버의 로컬 SSE 연결에만 전송한다.
    public void sendLocalRequestUpdated(Long requestId, String eventType) {
        List<SseEmitter> emitterList = emitters.get(requestId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        sendLocalRequestUpdated(requestId, eventType, getRequestForBroadcast(requestId));
    }

    private RemotePayCreateResponse getRequestForSse(Long requestId, Long userId) {
        if (requestId == null || userId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        RemotePayRequestEntity request = remotePayRequestRepository.findDetailByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

        return RemotePayCreateResponse.fromEntity(request);
    }

    // [be] 영은 260528 1110 | SSE 발행 시점마다 DB 최신 상태를 다시 읽어 화면 payload와 저장 상태를 맞춘다.
    private RemotePayCreateResponse getRequestForBroadcast(Long requestId) {
        RemotePayRequestEntity request = remotePayRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        return RemotePayCreateResponse.fromEntity(request);
    }

    private void sendLocalRequestUpdated(Long requestId, String eventType, RemotePayCreateResponse request) {
        List<SseEmitter> emitterList = emitters.get(requestId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        RemotePaySseEventResponse event = RemotePaySseEventResponse.of(eventType, request);
        for (SseEmitter emitter : new ArrayList<>(emitterList)) {
            sendEvent(emitter, requestId, "request-updated", event);
        }
    }

    private void sendEvent(SseEmitter emitter, Long requestId, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(requestId))
                    .data(data));
        } catch (IOException e) {
            log.warn("RemotePay SSE event send failed. requestId={}, eventName={}", requestId, eventName, e);
            emitter.completeWithError(e);
            removeEmitter(requestId, emitter);
        }
    }

    // [be] 영은 260528 1110 | 제거와 빈 목록 정리를 같은 compute 안에서 처리해 새 구독자 목록이 삭제되지 않게 한다.
    private void removeEmitter(Long requestId, SseEmitter emitter) {
        emitters.computeIfPresent(requestId, (key, emitterList) -> {
            emitterList.remove(emitter);
            return emitterList.isEmpty() ? null : emitterList;
        });
    }
}
