package com.erumpay.payment.merchant.domain.dto;

import java.time.LocalDateTime;

import com.erumpay.payment.core.domain.entity.CoreEntity;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
// [be] 나영은 260529 1638 | 오픈 SDK 결제 생성/조회 응답 DTO. 결제 주문 정보와 결제창 진입 정보를 함께 반환한다.
public class MerchantPaymentResponse {

    private Long paymentId;
    private String orderNo;
    private String merchantName;
    private Long amount;
    private String channel;
    private String status;
    private String redirectUrl;
    private String qrToken;
    private LocalDateTime paidAt;

    // [be] 나영은 260529 1638 | CoreEntity 필드를 SDK 응답 camelCase 계약으로 변환한다.
    public static MerchantPaymentResponse from(CoreEntity payment, String redirectUrl, String qrToken) {
        return MerchantPaymentResponse.builder()
                .paymentId(payment.getPaymentId())
                .orderNo(payment.getOrder_no())
                .merchantName(resolveMerchantName(payment))
                .amount(payment.getAmount())
                .channel(payment.getChannel_type() == null ? null : payment.getChannel_type().name())
                .status(payment.getPayment_status() == null ? null : payment.getPayment_status().name())
                .redirectUrl(redirectUrl)
                .qrToken(qrToken)
                .paidAt(payment.getPaidAt())
                .build();
    }

    private static String resolveMerchantName(CoreEntity payment) {
        return payment.getMerchant_name();
    }
}
