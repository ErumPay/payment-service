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
public class PaymentResultResponse {

    private Long paymentId;
    private String eventType;
    private Boolean applied;
    private Integer appliedCardCount;
    private String reason;
}
