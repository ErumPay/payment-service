package com.erumpay.payment.core.client.card.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CardBillingKeysResponse {
    private Long userId;
    private List<CardBillingKeyResponse> billingKeys;
}
