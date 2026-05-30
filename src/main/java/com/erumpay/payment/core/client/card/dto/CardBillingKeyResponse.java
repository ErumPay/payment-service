package com.erumpay.payment.core.client.card.dto;

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
public class CardBillingKeyResponse {
    private Long cardId;
    private Long userId;
    private Long cardProductId;
    private String billingKey;
    private String maskedNumber;

}
