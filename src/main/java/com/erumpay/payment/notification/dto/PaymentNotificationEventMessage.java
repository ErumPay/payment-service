package com.erumpay.payment.notification.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentNotificationEventMessage {

    private String eventId;
    private String eventType;
    private Long userId;
    private String title;
    private String content;
    private Long paymentId;
    private LocalDateTime occurredAt;
    private String correlationId;
}
