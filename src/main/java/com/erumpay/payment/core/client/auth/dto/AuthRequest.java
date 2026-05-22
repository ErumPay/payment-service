package com.erumpay.payment.core.client.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AuthRequest {
    private Long userId;
    private String pin;
}
