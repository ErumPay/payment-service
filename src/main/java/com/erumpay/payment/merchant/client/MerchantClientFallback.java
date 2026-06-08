package com.erumpay.payment.merchant.client;

import org.springframework.stereotype.Component;

import com.erumpay.payment.merchant.client.dto.ApiKeyValidationRequest;
import com.erumpay.payment.merchant.client.dto.ApiKeyValidationResponse;
import com.erumpay.payment.merchant.client.dto.MerchantResponse;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MerchantClientFallback implements MerchantClient {

    @Override
    public ApiKeyValidationResponse validateApiKey(ApiKeyValidationRequest request) {
        log.error("merchantClient fallback invoked for validateApiKey. apiKeyPresent={}",
                request != null && request.getApiKey() != null && !request.getApiKey().isBlank());
        throw new IllegalStateException("merchant-service api key validation is unavailable");
    }

    @Override
    public MerchantResponse merchantInfoRequest(Long merchantId) {
        log.warn("merchantClient fallback invoked for merchantInfoRequest. merchantId={}", merchantId);
        return MerchantResponse.builder()
                .merchantId(merchantId)
                .build();
    }
}
