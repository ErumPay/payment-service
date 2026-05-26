package com.erumpay.payment.core.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.core.client.auth.dto.AuthPinRequest;
import com.erumpay.payment.core.client.auth.dto.AuthPinResponse;

@FeignClient(name = "authClient", url = "${auth.base-url}")
public interface AuthClient {

    @PostMapping("/api/internal/auth/pin/verify")
    AuthPinResponse verifyPaymentPassword(
            @RequestBody AuthPinRequest request);
}