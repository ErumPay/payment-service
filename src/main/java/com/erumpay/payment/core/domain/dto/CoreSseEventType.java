package com.erumpay.payment.core.domain.dto;

public enum CoreSseEventType {
    CONNECTED,
    PAYMENT_PENDING,
    PAYMENT_FAILED,
    PAYMENT_PAID,
    RECOMMENDATION_SUCCEEDED,
    RECOMMENDATION_FAILED
}
