package com.erumpay.payment.core.domain.dto;

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
public class PaymentListResonse {

    private List<PaymentItem> items;
    private Long page;
    private Long count;
    private boolean hasNext;

    @Builder
    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PaymentItem {
        private Long paymentId;

        private String paymentType;
        private String strategyType;
        private String status;

        private Long amount;
        private String orderName;
        private LocalDateTime paidAt;
    }

}
