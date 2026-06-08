package com.erumpay.payment.core.domain.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAllFetchResponse {

    private Long userId;
    private LocalDate from;
    private LocalDate to;
    private Long totalAmount;
    private Long paymentCount;
    private List<MerchantUsage> merchantUsages;
    private List<CardUsage> cardUsages;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MerchantUsage {
        private String merchantName;
        private Long paymentCount;
        private Long paidAmount;
    }

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CardUsage {
        private Long cardId;
        private Long paymentCount;
        private Long paidAmount;
    }
}
