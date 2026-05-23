package com.erumpay.payment.dutch.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.dao.DutchPayParticipantRepository;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
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
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // [be] 영은 260523 1120 | 세션 참여 권한을 확인한 뒤 세션별 SSE 구독자를 등록한다
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

    // [be] 영은 260523 1120 | 더치페이 상태 변경을 같은 세션을 보고 있는 모든 구독자에게 전송한다
    public void publishSessionUpdated(
            Long sessionId,
            String eventType,
            DutchPaySessionDetailResponse session) {
        List<SseEmitter> emitterList = emitters.get(sessionId);
        if (emitterList == null || emitterList.isEmpty()) {
            return;
        }

        DutchPaySseEventResponse event = DutchPaySseEventResponse.of(eventType, session);
        for (SseEmitter emitter : emitterList) {
            sendEvent(emitter, sessionId, "session-updated", event);
        }
    }

    // [be] 영은 260523 1120 | SSE 구독 시작 시 요청자가 세션 대표자 또는 참여자인지 검증한다
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

    // [be] 영은 260523 1120 | 단일 emitter 전송 실패 시 연결을 종료하고 구독 목록에서 제거한다
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

    // [be] 영은 260523 1120 | 완료/타임아웃/오류가 난 SSE 연결을 세션별 목록에서 정리한다
    private void removeEmitter(Long sessionId, SseEmitter emitter) {
        List<SseEmitter> emitterList = emitters.get(sessionId);
        if (emitterList == null) {
            return;
        }

        emitterList.remove(emitter);
        if (emitterList.isEmpty()) {
            emitters.remove(sessionId);
        }
    }
}
