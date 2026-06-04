package com.erumpay.payment.remote.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Core /payment/prepare REMOTE 분기에서 원격결제 요청 생성을 위해 RemotePay로 전달하는 내부 요청")
public class RemotePayCoreCreateRequest {

    @NotNull
    @Positive
    @Schema(description = "QR 또는 주문 생성으로 먼저 만들어진 payment_orders.payment_id", example = "10001")
    private Long payment_id;

    @NotNull
    @Positive
    @Schema(description = "요청자가 결제를 부탁할 대리결제자 user_id. 저장/상태 관리는 RemotePay가 담당한다.", example = "3")
    private Long target_user_id;

    @Size(max = 200)
    @Schema(description = "요청자가 대리결제자에게 보여줄 원격결제 설명", example = "롯데시네마 홍대입구점")
    private String description;
}
