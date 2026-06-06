package com.erumpay.payment.core.client.recommend;

import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.ErrorCode;

import feign.FeignException;

@Component
public class RecommendFeignErrorMapper {

    public ErrorCode map(FeignException exception) {
        String body = exception.contentUTF8();
        if (containsReason(body, "INVALID_REQUEST")) {
            return ErrorCode.REC_REQUEST_INVALID;
        }
        if (containsReason(body, "CARD_SERVICE_CLIENT_ERROR")) {
            return ErrorCode.REC_CARD_SERVICE_CLIENT_ERROR;
        }
        if (containsReason(body, "CARD_SERVICE_UNAVAILABLE")) {
            return ErrorCode.REC_SERVICE_UNAVAILABLE;
        }
        if (containsReason(body, "INTERNAL_SERVER_ERROR")) {
            return ErrorCode.REC_INTERNAL_SERVER_ERROR;
        }
        if (exception.status() >= 400 && exception.status() < 500) {
            return ErrorCode.REC_REQUEST_INVALID;
        }
        if (exception.status() == 502) {
            return ErrorCode.REC_CARD_SERVICE_CLIENT_ERROR;
        }
        if (exception.status() == 503) {
            return ErrorCode.REC_SERVICE_UNAVAILABLE;
        }
        return ErrorCode.REC_INTERNAL_SERVER_ERROR;
    }

    private boolean containsReason(String body, String reason) {
        return body != null && body.contains(reason);
    }
}
