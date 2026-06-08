package com.erumpay.payment.merchant.client.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApiKeyValidationResponse {

    private Long merchantId;
    private boolean valid;
    private String merchantStatus;
    private String apiKeyStatus;
}
