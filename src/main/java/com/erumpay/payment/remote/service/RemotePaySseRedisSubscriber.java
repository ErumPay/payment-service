package com.erumpay.payment.remote.service;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.erumpay.payment.remote.domain.dto.RemotePaySseRedisMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class RemotePaySseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final RemotePaySseService remotePaySseService;

    // [be] 영은 260528 1110 | Redis에서 받은 원격결제 상태 변경 신호를 현재 인스턴스의 SSE 구독자에게만 전달한다.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            RemotePaySseRedisMessage event = objectMapper.readValue(body, RemotePaySseRedisMessage.class);
            if (event.getRequest_id() == null || event.getEvent_type() == null || event.getEvent_type().isBlank()) {
                log.warn("RemotePay SSE Redis message ignored. request_id={}, event_type={}",
                        event.getRequest_id(), event.getEvent_type());
                return;
            }

            remotePaySseService.sendLocalRequestUpdated(event.getRequest_id(), event.getEvent_type());
        } catch (Exception e) {
            log.warn("RemotePay SSE Redis message handling failed. channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
        }
    }
}
