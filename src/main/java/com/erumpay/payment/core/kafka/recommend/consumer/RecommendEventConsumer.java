package com.erumpay.payment.core.kafka.recommend.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.service.CoreSseService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class RecommendEventConsumer {
    private final CoreSseService coreSseService;

    @KafkaListener(topics = "${app.kafka.topics.recommend-event:recommend.event}", groupId = "${spring.kafka.consumer.group-id:payment-core}")
    public void onRecommendEvent(RecommendEventMessage message) {
        if (message == null || message.getPaymentId() == null || message.getEventType() == null) {
            log.warn("Ignore invalid recommend event message: {}", message);
            return;
        }

        switch (message.getEventType()) {
            case "RECOMMENDATION_COMPLETED" -> {
                log.info("Recommend completed event received. paymentId={}", message.getPaymentId());
                coreSseService.pushEvent(message.getPaymentId(), "recommendation", message);
            }
            case "RECOMMENDATION_FAILED" -> {
                log.info("Recommend failed event received. paymentId={}, reason={}, errorCode={}",
                        message.getPaymentId(), message.getReason(), message.getErrorCode());
                coreSseService.pushEvent(message.getPaymentId(), "recommendation", message);
            }
            default -> log.warn("Unknown recommend eventType={}", message.getEventType());
        }
    }
}
