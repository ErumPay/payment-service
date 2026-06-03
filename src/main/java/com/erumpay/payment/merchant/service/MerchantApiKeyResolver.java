package com.erumpay.payment.merchant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.merchant.client.MerchantClient;
import com.erumpay.payment.merchant.client.dto.ApiKeyValidationRequest;
import com.erumpay.payment.merchant.client.dto.ApiKeyValidationResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantApiKeyResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    private final MerchantClient merchantClient;

    @Value("${merchant.api-key-dev-fallback-enabled:false}")
    private boolean devFallbackEnabled;

    public Long resolveMerchantId(String authorization) {
        String apiKey = extractApiKey(authorization);

        try {
            ApiKeyValidationResponse response = merchantClient.validateApiKey(
                    new ApiKeyValidationRequest(apiKey));
            if (response == null || !response.isValid() || response.getMerchantId() == null) {
                log.warn("Merchant API key validation failed. valid={}, merchantId={}",
                        response == null ? null : response.isValid(),
                        response == null ? null : response.getMerchantId());
                throw new CustomException(ErrorCode.MERCHANT_API_KEY_INVALID);
            }

            return response.getMerchantId();
        } catch (CustomException e) {
            throw e;
        } catch (FeignException e) {
            return resolveWithDevFallbackOrThrow(apiKey, e);
        } catch (RuntimeException e) {
            return resolveWithDevFallbackOrThrow(apiKey, e);
        }
    }

    private String extractApiKey(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            log.warn("Merchant API authorization header is missing.");
            throw new CustomException(ErrorCode.MERCHANT_API_KEY_MISSING);
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            log.warn("Merchant API authorization header is not a Bearer token.");
            throw new CustomException(ErrorCode.MERCHANT_API_KEY_INVALID);
        }

        String apiKey = authorization.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isBlank()) {
            throw new CustomException(ErrorCode.MERCHANT_API_KEY_MISSING);
        }

        return apiKey;
    }

    private Long resolveWithDevFallbackOrThrow(String apiKey, RuntimeException e) {
        if (devFallbackEnabled) {
            Long merchantId = tryResolveDevMerchantId(apiKey);
            if (merchantId != null) {
                log.warn("Merchant API key validation unavailable. Using local dev fallback. merchantId={}",
                        merchantId,
                        e);
                return merchantId;
            }
        }

        log.error("Merchant API key validation unavailable.", e);
        throw new CustomException(ErrorCode.MERCHANT_AUTH_UNAVAILABLE, e);
    }

    private Long tryResolveDevMerchantId(String apiKey) {
        String idPart = null;
        if (apiKey.startsWith("merchant_")) {
            idPart = apiKey.substring("merchant_".length()).split("_", 2)[0];
        } else if (apiKey.startsWith("test_")) {
            idPart = apiKey.substring("test_".length()).split("_", 2)[0];
        }

        if (idPart == null || idPart.isBlank()) {
            return null;
        }

        try {
            return Long.valueOf(idPart);
        } catch (NumberFormatException e) {
            log.warn("Merchant API key contains non-numeric merchant id. idPart={}", idPart);
            return null;
        }
    }
}
