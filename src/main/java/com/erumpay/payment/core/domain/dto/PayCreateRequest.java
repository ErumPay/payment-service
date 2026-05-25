package com.erumpay.payment.core.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PayCreateRequest {

    @NotNull
    private Long paymentId;

    @NotNull
    @Positive
    private Long totalAmount;

    @NotEmpty
    private List<CardPortion> cards;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CardPortion {

        @NotNull
        @Positive
        private Long cardId;

        @NotNull
        @Positive
        private Long amount;
    }

}
