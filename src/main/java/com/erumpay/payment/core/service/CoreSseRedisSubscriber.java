package com.erumpay.payment.core.service;

import java.nio.charset.StandardCharsets;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.erumpay.payment.core.domain.dto.CoreSseRedisMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CoreSseRedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final CoreSseService coreSseService;

    // [be] 다윤 260601 | Redis에서 받은 코어 결제 상태 변경 SSE 이벤트를 현재 인스턴스의 로컬 연결에 전달한다.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        log.info("onMessage called.");

        try {
            CoreSseRedisMessage redisMessage = objectMapper.readValue(body, CoreSseRedisMessage.class);
            if (redisMessage.getPayment_id() == null
                    || redisMessage.getEvent() == null
                    || redisMessage.getEvent().getEventType() == null) {
                log.warn("Core SSE Redis message ignored. payment_id={}, eventType={}",
                        redisMessage.getPayment_id(),
                        redisMessage.getEvent() == null ? null : redisMessage.getEvent().getEventType());
                return;
            }

            coreSseService.applyPaymentUpdatedFromRedis(redisMessage.getPayment_id(), redisMessage.getEvent());
        } catch (Exception e) {
            log.warn("Core SSE Redis message handling failed. channel={}",
                    new String(message.getChannel(), StandardCharsets.UTF_8), e);
        }
    }
}
