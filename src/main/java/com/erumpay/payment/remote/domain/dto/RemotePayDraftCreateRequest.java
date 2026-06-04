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
@Schema(description = "Core /payment/prepare REMOTE 단계에서 대리결제자 선택 전 원격결제 draft를 생성하는 내부 요청")
public class RemotePayDraftCreateRequest {

    @NotNull
    @Positive
    @Schema(description = "QR에서 먼저 생성된 원본 payment_orders.payment_id", example = "10001")
    private Long source_payment_id;

    @Size(max = 200)
    @Schema(description = "요청자/대리결제자에게 보여줄 원격결제 설명", example = "롯데시네마 홍대입구점")
    private String description;
}
