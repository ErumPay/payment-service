package com.erumpay.payment.core.client.pg.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PgAuthPayResponse {

    private Long pgTxnId;

    private Long payPaymentId;

    private Long merchantId;

    private String txnType;

    private String status;

    private Long amount;

    private String pgApprovalNumber;

    private String cardApprovalNumber;

    private String rejectReason;

    private String failureCode;

    private String failureMessage;

    private LocalDateTime approvedAt;

    private LocalDateTime processedAt;

}
