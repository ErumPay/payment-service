package com.erumpay.payment.core.domain.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CardPaymentHistoryResponse {

    private Long cardId;
    private List<PaymentItem> payments;

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentItem {
        private String merchantName;
        private LocalDateTime paidAt;
        private Long amount;
        private String status;
    }
}
