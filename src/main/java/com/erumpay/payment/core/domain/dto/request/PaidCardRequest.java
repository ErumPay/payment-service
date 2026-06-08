package com.erumpay.payment.core.domain.dto.request;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PaidCardRequest {

    private Long paymentId;
    private Long pgTxnId;
    private String pgApprovalNum;

    private Long cardId;
    private String maskedNumber;
    private String cardName;
    private Long paidAmount;
    private Long totalBenefitAmount;
    private String benefitDesc;
    private LocalDateTime paidAt;
}
