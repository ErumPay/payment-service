package com.erumpay.payment.notification.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentSettlementEventMessage {
    private String eventId;
    private String eventType;

    private Long merchantId;
    private Long paymentId;
    private Long amount;

    private LocalDateTime occurredAt;

}
