package com.erumpay.payment.merchant.domain.dto;

import java.time.LocalDateTime;

import com.erumpay.payment.core.domain.dto.response.CanceledResponse;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
// [be] 나영은 260529 1638 | 오픈 SDK 결제 취소 응답 DTO. Core 취소 응답의 paymentStatus를 SDK의
// status 필드로 맞춘다.
public class MerchantCancelResponse {

    private Long paymentId;
    private String status;
    private LocalDateTime canceledAt;

    public static MerchantCancelResponse from(CanceledResponse response) {
        return MerchantCancelResponse.builder()
                .paymentId(response.getPaymentId())
                .status(response.getPaymentStatus())
                .canceledAt(response.getCanceledAt())
                .build();
    }
}
