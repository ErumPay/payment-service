package com.erumpay.payment.remote.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Core가 대리결제자용 실제 payment_order를 생성한 뒤 원격결제 요청에 연결하는 내부 요청")
public class RemotePayPaymentConnectRequest {

    @NotNull
    @Positive
    @Schema(description = "대리결제자가 실제로 결제할 payment_orders.payment_id", example = "10002")
    private Long payer_payment_id;
}
