package com.erumpay.payment.core.kafka.recommend.producer;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class RecommendCommandMessage {
    private String eventType;
    private Long paymentId;
    private Long userId;
    private Long amount;
}
