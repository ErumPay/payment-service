package com.erumpay.payment.core.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserWithdrawalResponse {

    private boolean possibility;
    private Long userId;
    private boolean hasUnpaidPayments;
    private Long unpaidPaymentCount;
    private String message;
}
