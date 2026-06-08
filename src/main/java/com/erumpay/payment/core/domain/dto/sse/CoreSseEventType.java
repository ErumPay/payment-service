package com.erumpay.payment.core.domain.dto.sse;

public enum CoreSseEventType {
    CONNECTED,
    PAYMENT_PENDING,
    PG_PENDING,
    PAYMENT_FAILED,
    PAYMENT_AUTHORIZED,
    PAYMENT_PAID,
    RECOMMENDATION_SUCCEEDED,
    RECOMMENDATION_FAILED
}
