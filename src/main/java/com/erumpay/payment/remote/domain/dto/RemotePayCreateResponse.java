package com.erumpay.payment.remote.domain.dto;

import java.time.LocalDateTime;

import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RemotePayCreateResponse {

    private Long request_id;
    private Long requester_user_id;
    private Long target_user_id;
    private Long payment_id;
    private Long amount;
    private String description;
    private String status;
    private LocalDateTime expires_at;
    private LocalDateTime created_at;

    public static RemotePayCreateResponse fromEntity(RemotePayRequestEntity request) {
        return RemotePayCreateResponse.builder()
                .request_id(request.getRequest_id())
                .requester_user_id(request.getRequester_user_id())
                .target_user_id(request.getTarget_user_id())
                .payment_id(request.getPayment() == null ? null : request.getPayment().getPaymentId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(request.getStatus().name())
                .expires_at(request.getExpires_at())
                .created_at(request.getCreated_at())
                .build();
    }
}
