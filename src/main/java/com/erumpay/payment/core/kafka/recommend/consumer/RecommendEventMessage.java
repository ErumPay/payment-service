package com.erumpay.payment.core.kafka.recommend.consumer;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString
public class RecommendEventMessage {
    private String eventType;
    private Long paymentId;
    private String reason;
    private String errorCode;
}
