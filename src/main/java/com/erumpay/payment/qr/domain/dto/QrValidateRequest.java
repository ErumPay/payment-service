package com.erumpay.payment.qr.domain.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class QrValidateRequest {
    private String token;
}
