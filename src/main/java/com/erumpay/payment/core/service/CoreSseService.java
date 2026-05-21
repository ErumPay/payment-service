package com.erumpay.payment.core.service;

import java.io.IOException;
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

    // [be] 다윤 260521 SSE 로직 변경 예정

    private static final long SSE_TIMEOUT_MS = 60L * 60L * 1000L;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long paymentId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        emitters.computeIfAbsent(paymentId, key -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(paymentId, emitter));
        emitter.onTimeout(() -> removeEmitter(paymentId, emitter));
        emitter.onError(error -> removeEmitter(paymentId, emitter));

        sendConnectedEvent(emitter, paymentId);
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
        List<SseEmitter> emitterList = emitters.get(paymentId);
        if (emitterList == null)
            return;

        for (SseEmitter emitter : emitterList) {
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
