package com.erumpay.payment.core.client.card.dto;

import java.time.LocalDateTime;
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
public class PaymentResultRequest {

    private Long paymentId;
    private String eventType;
    private LocalDateTime occurredAt;
    private List<Card> cards;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Card {

        private Long paymentCardId;
        private Long cardId;
        private Long approvedAmount;
        private LocalDateTime approvedAt;
        private AppliedBenefit appliedBenefit;
    }

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class AppliedBenefit {

        private Long benefitId;
        private Long tierId;
        private Long benefitAmount;
    }
}