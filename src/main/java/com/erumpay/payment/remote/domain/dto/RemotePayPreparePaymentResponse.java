package com.erumpay.payment.remote.domain.dto;

import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RemotePayPreparePaymentResponse {

    private Long request_id;
    private Long payment_id;
    private Long target_user_id;
    private Long amount;
    private String request_status;
    private String payment_status;

    public static RemotePayPreparePaymentResponse fromEntity(RemotePayRequestEntity request, CoreEntity payment) {
        return RemotePayPreparePaymentResponse.builder()
                .request_id(request.getRequest_id())
                .payment_id(payment.getPaymentId())
                .target_user_id(request.getTarget_user_id())
                .amount(request.getAmount())
                .request_status(request.getStatus().name())
                .payment_status(payment.getPayment_status().name())
                .build();
    }
}
