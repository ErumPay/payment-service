package com.erumpay.payment.merchant.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.merchant.client.dto.ApiKeyValidationRequest;
import com.erumpay.payment.merchant.client.dto.ApiKeyValidationResponse;
import com.erumpay.payment.merchant.client.dto.MerchantResponse;

@FeignClient(name = "merchantClient", url = "${merchant.base-url}")
public interface MerchantClient {

    @PostMapping(value = "/internal/v1/merchants/api-key/validate", consumes = "application/json")
    ApiKeyValidationResponse validateApiKey(@RequestBody ApiKeyValidationRequest request);

    @GetMapping(value = "/api/v1/pg-admin/merchants/{merchantId}")
    MerchantResponse merchantInfoRequest(@PathVariable("merchantId") Long merchantId);
}
