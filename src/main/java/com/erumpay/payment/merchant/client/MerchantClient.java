package com.erumpay.payment.merchant.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.merchant.client.dto.ApiKeyValidationRequest;
import com.erumpay.payment.merchant.client.dto.ApiKeyValidationResponse;

@FeignClient(name = "merchantClient", url = "${merchant.base-url}")
public interface MerchantClient {

    @PostMapping(value = "/internal/v1/merchants/api-key/validate", consumes = "application/json")
    ApiKeyValidationResponse validateApiKey(@RequestBody ApiKeyValidationRequest request);
}
