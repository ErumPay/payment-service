package com.erumpay.payment.core.client.recommend.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@ToString
public class RecommendResponse {

    private Long paymentId;
    private LocalDateTime recommendedAt;
    private List<Result> results;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    public static class Result {
        private String strategyType;
        private Long totalBenefitAmount;
        private List<Card> cards;
        private String reason;
    }

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        private Long cardId;
        private Long cardProductId;
        private String cardCompany;
        private String cardName;
        private String maskedNumber;
        private Long amount;
        private Long discountAmount;
        private Long cashbackAmount;
        private Long mileageAmount;
        private Long totalBenefitAmount;
        private Long currentPerformanceAmount;
        private Long targetPerformanceAmount;
        private Long remainingToTarget;
        private Long expectedPerformanceAmount;
        private Boolean willReachTarget;
        private List<String> warnings;
    }
}
