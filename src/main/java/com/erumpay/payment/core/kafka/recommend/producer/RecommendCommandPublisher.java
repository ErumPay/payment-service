package com.erumpay.payment.core.kafka.recommend.producer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.domain.entity.CoreEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RecommendCommandPublisher {
    private static final String EVENT_TYPE_RECOMMENDATION_REQUESTED = "RECOMMENDATION_REQUESTED";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.recommend-command:recommend.command}")
    private String recommendCommandTopic;

    public void publishRecommendationRequested(CoreEntity payment) {
        RecommendCommandMessage message = RecommendCommandMessage.builder()
                .eventType(EVENT_TYPE_RECOMMENDATION_REQUESTED)
                .paymentId(payment.getPaymentId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .build();

        kafkaTemplate.send(recommendCommandTopic, String.valueOf(payment.getPaymentId()), message);
        log.info("Published recommend command. topic={}, paymentId={}, eventType={}",
                recommendCommandTopic,
                payment.getPaymentId(),
                EVENT_TYPE_RECOMMENDATION_REQUESTED);
    }
}
