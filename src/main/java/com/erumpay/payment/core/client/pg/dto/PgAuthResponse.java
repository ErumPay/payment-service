package com.erumpay.payment.core.client.pg.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PgAuthResponse {

    private Long pgTxnId;

    private Long payPaymentId;

    private Long merchantId;

    private String txnType;

    private String pgStatus;

    private Long amount;

    private String pgApprovalNumber;

    private String cardApprovalNumber;

    private String rejectReason;

    private String failureCode;

    private String failureMessage;

    private LocalDate approvedAt;

    private LocalDate processedAt;

}
