package com.erumpay.payment.remote.domain.dto;

import java.time.LocalDateTime;

import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "원격결제 요청 생성/조회/상태 변경 응답. request_id는 프론트와 Core가 원격결제 흐름을 추적하는 remoteRequestId로 사용한다.")
public class RemotePayCreateResponse {

    @Schema(description = "원격결제 요청 ID. 더치페이의 dutchSessionId와 같은 추적 키 역할을 한다.", example = "2001")
    private Long request_id;
    @Schema(description = "원격결제를 요청한 사용자 ID", example = "1")
    private Long requester_user_id;
    @Schema(description = "실제로 결제할 대리결제자 사용자 ID", example = "3")
    private Long target_user_id;
    @Deprecated
    @Schema(description = "Deprecated. 대리결제자 실제 결제 ID 호환 필드. 신규 연동은 payer_payment_id를 사용한다.", example = "10002")
    private Long payment_id;
    @Schema(description = "QR에서 먼저 생성된 원본 payment_orders.payment_id", example = "10001")
    private Long source_payment_id;
    @Schema(description = "대리결제자가 실제로 결제할 payment_orders.payment_id", example = "10002")
    private Long payer_payment_id;
    @Schema(description = "원격결제 금액", example = "10000")
    private Long amount;
    private String description;
    @Schema(description = "원격결제 요청 상태", example = "PENDING")
    private String status;
    private String reject_reason;
    private LocalDateTime expires_at;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime completed_at;

    // [be] 영은 260527 1010 | 요청 생성/조회/취소/거절 응답을 하나로 맞춰 프론트가 같은 모델로 화면을 갱신하게 한다.
    // [be] 영은 260605 1600 | payment_id는 기존 응답 호환용 alias이고, 신규 연동은
    // source_payment_id/payer_payment_id를 구분해서 사용한다.
    public static RemotePayCreateResponse fromEntity(RemotePayRequestEntity request) {
        return RemotePayCreateResponse.builder()
                .request_id(request.getRequest_id())
                .requester_user_id(request.getRequester_user_id())
                .target_user_id(request.getTarget_user_id())
                .payment_id(request.getPayment() == null ? null : request.getPayment().getPaymentId())
                .source_payment_id(request.getSource_payment_id())
                .payer_payment_id(request.getPayment() == null ? null : request.getPayment().getPaymentId())
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
