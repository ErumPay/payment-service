package com.erumpay.payment.core.domain.dto;

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
public class PaymentDetailResponse {

    private Long userId;
    private Long paymentId;

    private String paymentType;
    private String strategyType;
    private String status;

    private Long amount;
    private String orderName;
    private String orderNo;
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;

}
