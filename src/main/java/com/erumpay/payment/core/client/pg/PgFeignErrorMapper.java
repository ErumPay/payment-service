package com.erumpay.payment.core.client.pg;

import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.ErrorCode;

import feign.FeignException;

@Component
public class PgFeignErrorMapper {

    public ErrorCode map(
            FeignException exception,
            ErrorCode clientFallback,
            ErrorCode serverFallback) {
        String body = exception.contentUTF8();
        if (containsPgReason(body, "INVALID_REQUEST")) {
            return ErrorCode.PG_REQUEST_INVALID;
        }
        if (containsPgReason(body, "AUTHORIZATION_REQUIRED")) {
            return ErrorCode.PG_AUTHORIZATION_REQUIRED;
        }
        if (containsPgReason(body, "PG_PAYMENT_NOT_FOUND")) {
            return ErrorCode.PG_PAYMENT_TRANSACTION_NOT_FOUND;
        }
        if (containsPgReason(body, "INVALID_TRANSACTION_STATE")) {
            return ErrorCode.PG_TRANSACTION_STATE_INVALID;
        }
        if (containsPgReason(body, "PAYMENT_ALREADY_CANCELLED")) {
            return ErrorCode.PG_PAYMENT_ALREADY_CANCELLED;
        }
        if (containsPgReason(body, "AUTH_ONLY_ALREADY_VOIDED")) {
            return ErrorCode.PG_AUTH_ONLY_ALREADY_VOIDED;
        }
        if (containsPgReason(body, "ORIGINAL_TRANSACTION_MISMATCH")) {
            return ErrorCode.PG_ORIGINAL_TRANSACTION_MISMATCH;
        }
        if (containsPgReason(body, "DUPLICATE_IDEMPOTENCY_KEY")) {
            return ErrorCode.PG_DUPLICATE_IDEMPOTENCY_KEY;
        }
        if (containsPgReason(body, "BILLING_KEY_CIRCUIT_OPEN")) {
            return ErrorCode.PG_BILLING_KEY_CIRCUIT_OPEN;
        }
        if (containsPgReason(body, "BILLING_KEY_TIMEOUT")) {
            return ErrorCode.PG_BILLING_KEY_TIMEOUT;
        }
        if (containsPgReason(body, "CARD_CIRCUIT_OPEN")) {
            return ErrorCode.PG_CARD_CIRCUIT_OPEN;
        }
        if (containsPgReason(body, "CARD_TIMEOUT")) {
            return ErrorCode.PG_CARD_TIMEOUT;
        }
        if (containsPgReason(body, "COMPENSATION_FAILED")) {
            return ErrorCode.PG_COMPENSATION_CANCEL_FAILED;
        }
        if (containsPgReason(body, "INTERNAL_SERVER_ERROR")) {
            return ErrorCode.PG_INTERNAL_ERROR;
        }
        if (exception.status() == 401) {
            return ErrorCode.PG_AUTHORIZATION_REQUIRED;
        }
        if (exception.status() == 404) {
            return ErrorCode.PG_PAYMENT_TRANSACTION_NOT_FOUND;
        }
        if (exception.status() == 408 || exception.status() == 504) {
            return ErrorCode.PG_TIMEOUT;
        }
        if (exception.status() >= 400 && exception.status() < 500) {
            return clientFallback;
        }
        return serverFallback;
    }

    private boolean containsPgReason(String body, String reason) {
        return body != null && body.contains(reason);
    }
}
