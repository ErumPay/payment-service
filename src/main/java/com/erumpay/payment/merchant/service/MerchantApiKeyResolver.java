package com.erumpay.payment.merchant.service;

import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MerchantApiKeyResolver {

    // [be] 나영은 260529 1638 | 임시 SDK 테스트용 resolver. 추후 merchant-service API Key 검증 API 연동으로 교체한다.
    public Long resolveMerchantId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            log.warn("Merchant API authorization header is missing or invalid.");
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String token = authorization.substring("Bearer ".length()).trim();
        String idPart = null;
        // [be] 나영은 260529 1638 | 로컬 테스트에서는 merchant_1_xxx 또는 test_1_xxx 형태에서 merchantId만 추출한다.
        if (token.startsWith("merchant_")) {
            idPart = token.substring("merchant_".length()).split("_", 2)[0];
        } else if (token.startsWith("test_")) {
            idPart = token.substring("test_".length()).split("_", 2)[0];
        }

        if (idPart == null || idPart.isBlank()) {
            log.warn("Merchant API key format is invalid.");
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            log.warn("Merchant API key contains non-numeric merchant id. idPart={}", idPart);
            throw new CustomException(ErrorCode.FORBIDDEN, e);
        }
    }
}
