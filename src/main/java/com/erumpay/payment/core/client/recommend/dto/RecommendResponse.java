package com.erumpay.payment.core.client.recommend.dto;

import java.time.LocalDateTime;
import java.util.List;

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
// @JsonIgnoreProperties(ignoreUnknown = true)
public class RecommendResponse {

    private Long paymentId;
    private LocalDateTime recommendedAt;
    private List<Result> results;

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    // @JsonIgnoreProperties(ignoreUnknown = true)
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
    // @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Card {
        private Long paymentCardId;
        private Long cardId;
        private Long cardProductId;
        private String cardCompany;
        private String cardName;
        private String imageUrl;
        private String maskedNumber;
        private Long approvedAmount;
        private Long amount;
        private AppliedBenefit appliedBenefit;
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

    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @Getter
    @ToString
    // @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppliedBenefit {
        private Long benefitId;
        private Long tierId;
        private Long benefitAmount;
    }
}
