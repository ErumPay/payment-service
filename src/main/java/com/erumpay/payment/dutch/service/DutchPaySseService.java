package com.erumpay.payment.dutch.service;

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
import com.erumpay.payment.dutch.dao.DutchPayParticipantRepository;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
import com.erumpay.payment.dutch.domain.dto.DutchPaySseRedisMessage;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPaySseEventResponse;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DutchPaySseService {

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;

    private final DutchPaySessionRepository dutchPaySessionRepository;
    private final DutchPayParticipantRepository dutchPayParticipantRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DutchPaySseTopicProperties dutchPaySseTopicProperties;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // [be] 영은 260523 1220 | 세션 참여 권한을 확인한 뒤 세션별 SSE 구독자를 등록한다
    public SseEmitter subscribe(Long sessionId, Long userId) {
        DutchPaySessionDetailResponse session = getSessionForSse(sessionId, userId);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(sessionId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(sessionId, emitter));
        emitter.onTimeout(() -> removeEmitter(sessionId, emitter));
        emitter.onError(error -> removeEmitter(sessionId, emitter));

        sendEvent(emitter, sessionId, "connected", DutchPaySseEventResponse.of("CONNECTED", session));
        return emitter;
    }

    // [be] 영은 260523 1220 | 더치페이 상태 변경을 같은 세션을 보고 있는 모든 구독자에게 전송한다
    public void publishSessionUpdated(
            Long sessionId,
            String eventType,
            DutchPaySessionDetailResponse session) {
        try {
            DutchPaySseRedisMessage message = DutchPaySseRedisMessage.of(sessionId, eventType);
            stringRedisTemplate.convertAndSend(
                    dutchPaySseTopicProperties.getSessionEvents(),
                    objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("DutchPay SSE Redis publish failed. fallback to local emit. sessionId={}, eventType={}",
                    sessionId, eventType, e);
            sendLocalSessionUpdated(sessionId, eventType, session);
        }
    }

    // [be] 영은 260526 1020 | Redis에서 받은 세션 변경 이벤트를 현재 서버에 연결된 SSE 구독자에게만 전송한다
    public void sendLocalSessionUpdated(Long sessionId, String eventType) {
        List<SseEmitter> emitterList = emitters.get(sessionId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        sendLocalSessionUpdated(sessionId, eventType, getSessionForBroadcast(sessionId));
    }

    // [be] 영은 260523 1220 | SSE 구독 시작 시 요청자가 세션 대표자 또는 참여자인지 검증한다
    private DutchPaySessionDetailResponse getSessionForSse(Long sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        List<DutchPayParticipantEntity> participants =
                dutchPayParticipantRepository.findBySessionIdOrderByParticipantId(sessionId);
        if (!session.getHost_user_id().equals(userId)
                && participants.stream().noneMatch(participant -> participant.getUser_id().equals(userId))) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    // [be] 영은 260526 1020 | Redis Pub/Sub 수신 후 최신 DB 상태를 다시 읽어 SSE payload를 만든다
    private DutchPaySessionDetailResponse getSessionForBroadcast(Long sessionId) {
        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        List<DutchPayParticipantEntity> participants =
                dutchPayParticipantRepository.findBySessionIdOrderByParticipantId(sessionId);

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    // [be] 영은 260526 1020 | 한 서버 안에 연결된 구독자 목록을 복사해 전송 중 목록 변경과 충돌하지 않게 한다
    private void sendLocalSessionUpdated(
            Long sessionId,
            String eventType,
            DutchPaySessionDetailResponse session) {
        List<SseEmitter> emitterList = emitters.get(sessionId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        DutchPaySseEventResponse event = DutchPaySseEventResponse.of(eventType, session);
        for (SseEmitter emitter : new ArrayList<>(emitterList)) {
            sendEvent(emitter, sessionId, "session-updated", event);
        }
    }

    // [be] 영은 260523 1220 | 단일 emitter 전송 실패 시 연결을 종료하고 구독 목록에서 제거한다
    private void sendEvent(SseEmitter emitter, Long sessionId, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(sessionId))
                    .data(data));
        } catch (IOException e) {
            log.warn("DutchPay SSE event send failed. sessionId={}, eventName={}", sessionId, eventName, e);
            emitter.completeWithError(e);
            removeEmitter(sessionId, emitter);
        }
    }

    // [be] 영은 260523 1220 | 제거와 빈 목록 정리를 같은 compute 안에서 처리해 새 구독자 목록 삭제를 막는다
    private void removeEmitter(Long sessionId, SseEmitter emitter) {
        emitters.computeIfPresent(sessionId, (key, emitterList) -> {
            emitterList.remove(emitter);
            return emitterList.isEmpty() ? null : emitterList;
        });
    }
}
