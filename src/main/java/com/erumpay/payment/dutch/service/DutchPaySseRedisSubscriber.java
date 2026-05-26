package com.erumpay.payment.dutch.service;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.erumpay.payment.dutch.domain.dto.DutchPaySseRedisMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class DutchPaySseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final DutchPaySseService dutchPaySseService;

    // Applies Redis session-change messages to SSE connections on this instance.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody(), StandardCharsets.UTF_8);
            DutchPaySseRedisMessage event = objectMapper.readValue(body, DutchPaySseRedisMessage.class);
            if (event.getSession_id() == null || event.getEvent_type() == null || event.getEvent_type().isBlank()) {
                log.warn("DutchPay SSE Redis message ignored. session_id={}, event_type={}",
                        event.getSession_id(), event.getEvent_type());
                return;
            }

            dutchPaySseService.sendLocalSessionUpdated(event.getSession_id(), event.getEvent_type());
        } catch (Exception e) {
            log.warn("DutchPay SSE Redis message handling failed. channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
        }
    }
}
