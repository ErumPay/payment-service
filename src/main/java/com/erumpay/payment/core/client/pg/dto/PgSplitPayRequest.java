package com.erumpay.payment.core.client.pg.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PgSplitPayRequest {

    private Long payPaymentId;

    private Long merchantId;

    private Long totalAmount;

    private List<Payment> payments;

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payment {

        private Integer splitSeq;

        private String billingKey;

        private Long originalAmount;

        private Long approvedAmount;
    }
}
