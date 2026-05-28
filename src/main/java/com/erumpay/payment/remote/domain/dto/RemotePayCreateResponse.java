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
    private String reject_reason;
    private LocalDateTime expires_at;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime completed_at;

    // [be] 영은 260527 1010 | 요청 생성/조회/취소/거절 응답을 하나로 맞춰 프론트가 같은 모델로 화면을 갱신하게 한다.
    // [be] 영은 260527 1010 | payment_id는 prepare 전에는 null이고, prepare 이후에는 연결된 payment_orders 식별자로 내려간다.
    public static RemotePayCreateResponse fromEntity(RemotePayRequestEntity request) {
        return RemotePayCreateResponse.builder()
                .request_id(request.getRequest_id())
                .requester_user_id(request.getRequester_user_id())
                .target_user_id(request.getTarget_user_id())
                .payment_id(request.getPayment() == null ? null : request.getPayment().getPaymentId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .status(request.getStatus().name())
                .reject_reason(request.getReject_reason())
                .expires_at(request.getExpires_at())
                .created_at(request.getCreated_at())
                .updated_at(request.getUpdated_at())
                .completed_at(request.getCompleted_at())
                .build();
    }
}
