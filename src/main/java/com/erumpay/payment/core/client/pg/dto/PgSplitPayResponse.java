package com.erumpay.payment.core.client.pg.dto;

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
public class PgSplitPayResponse {

    private Long pgGroupId;

    private Long payPaymentId;

    private Long merchantId;

    private Long totalAmount;

    private String status;

    private String failureCode;

    private String failureReason;

    private String failureMessage;

    private List<Item> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        private Long pgTxnId;

        private Long pgGroupId;

        private Integer splitSeq;

        private Long originalTxnId;

        private Long payPaymentId;

        private Long merchantId;

        private String txnType;

        private String status;

        private Long amount;

        private String pgApprovalNumber;

        private String cardApprovalNumber;

        private String rejectReason;

        private String failureCode;

        private String failureReason;

        private String failureMessage;

        private LocalDateTime approvedAt;

        private LocalDateTime processedAt;
    }
}
