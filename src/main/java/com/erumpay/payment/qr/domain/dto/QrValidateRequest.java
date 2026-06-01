package com.erumpay.payment.qr.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QrValidateRequest {
    @Schema(description = "QR 토큰")
    private String token;
}
