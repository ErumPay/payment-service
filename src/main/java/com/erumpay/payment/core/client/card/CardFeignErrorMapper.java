package com.erumpay.payment.core.client.card;

import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.ErrorCode;

import feign.FeignException;

@Component
public class CardFeignErrorMapper {

    public ErrorCode map(
            FeignException exception,
            ErrorCode clientFallback,
            ErrorCode serverFallback) {
        String body = exception.contentUTF8();
        if (containsCardReason(body, "INVALID_PAYMENT_USAGE_EVENT_REQUEST")) {
            return ErrorCode.CARD_PAYMENT_RESULT_INVALID;
        }
        if (containsCardReason(body, "INVALID_REQUEST")) {
            return ErrorCode.CARD_REQUEST_INVALID;
        }
        if (containsCardReason(body, "AUTHORIZATION_REQUIRED")) {
            return ErrorCode.CARD_AUTHORIZATION_REQUIRED;
        }
        if (containsCardReason(body, "CARD_NOT_ACTIVE")) {
            return ErrorCode.CARD_NOT_ACTIVE;
        }
        if (containsCardReason(body, "CARD_NOT_FOUND")) {
            return ErrorCode.CARD_PAYMENT_CARD_NOT_FOUND;
        }
        if (containsCardReason(body, "BILLING_KEY_NOT_FOUND")) {
            return ErrorCode.CARD_BILLING_KEY_NOT_FOUND;
        }
        if (containsCardReason(body, "INTERNAL_SERVER_ERROR")) {
            return ErrorCode.CARD_INTERNAL_SERVER_ERROR;
        }
        if (exception.status() == 401) {
            return ErrorCode.CARD_AUTHORIZATION_REQUIRED;
        }
        if (exception.status() == 404) {
            return ErrorCode.CARD_PAYMENT_CARD_NOT_FOUND;
        }
        if (exception.status() >= 400 && exception.status() < 500) {
            return clientFallback;
        }
        return serverFallback;
    }

    private boolean containsCardReason(String body, String reason) {
        return body != null && body.contains(reason);
    }
}
