package com.erumpay.payment.merchant.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyValidationRequest {

    private String apiKey;
}
