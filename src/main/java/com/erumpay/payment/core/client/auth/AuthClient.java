package com.erumpay.payment.core.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.core.client.auth.dto.AuthRequest;
import com.erumpay.payment.core.client.auth.dto.AuthResponse;

@FeignClient(name = "authClient", url = "${auth.base-url}")
public interface AuthClient {

    @PostMapping("/api/v1/internal/auth/pin/verify")
    AuthResponse verifyPaymentPassword(
            @RequestBody AuthRequest request);
}