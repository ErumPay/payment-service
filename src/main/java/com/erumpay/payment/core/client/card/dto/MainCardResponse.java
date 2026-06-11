package com.erumpay.payment.core.client.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MainCardResponse {

    private Long cardId;
    private Long userId;
    private String maskedNumber;
    private String cardCompany;
    private String cardName;

}
