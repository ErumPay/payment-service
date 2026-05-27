package com.erumpay.payment.core.client.pg.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PgPayCancelRequest {

    private Long payPaymentId;
    private Long merchantId;
    private String cancelReason;

}
