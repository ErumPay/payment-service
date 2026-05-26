package com.erumpay.payment.core.client.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PgAuthPayRequest {

    private Long payPaymentId;

    private Long merchantId;

    private String billingKey;

    private Long originalAmount;
    private Long approvedAmount;

}
