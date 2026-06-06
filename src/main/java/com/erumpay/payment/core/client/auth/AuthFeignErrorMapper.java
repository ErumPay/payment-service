package com.erumpay.payment.core.client.auth;

import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.ErrorCode;

import feign.FeignException;

@Component
public class AuthFeignErrorMapper {

    public ErrorCode mapVerifyPinError(FeignException exception) {
        String body = exception.contentUTF8();
        if (containsReason(body, "PIN_VERIFY_FAILED")) {
            return ErrorCode.PIN_VERIFY_FAILED;
        }
        if (containsReason(body, "PIN_LOCKED")) {
            return ErrorCode.PIN_LOCKED;
        }
        if (containsReason(body, "PIN_RESET_REQUIRED")) {
            return ErrorCode.PIN_RESET_REQUIRED;
        }
        if (containsReason(body, "AUTHORIZATION_REQUIRED")) {
            return ErrorCode.AUTH_ACCESS_DENIED;
        }
        if (exception.status() == 400) {
            return ErrorCode.AUTH_REQUEST_INVALID;
        }
        if (exception.status() == 401) {
            return ErrorCode.PIN_VERIFY_FAILED;
        }
        if (exception.status() == 403) {
            return ErrorCode.AUTH_ACCESS_DENIED;
        }
        if (exception.status() == 404) {
            return ErrorCode.PIN_NOT_SET;
        }
        if (exception.status() == 423) {
            return ErrorCode.PIN_LOCKED;
        }
        return ErrorCode.INTERNAL_AUTH_SERVER_ERROR;
    }

    private boolean containsReason(String body, String reason) {
        return body != null && body.contains(reason);
    }
}
