package com.erumpay.payment.qr.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class QrRequest {
    @Schema(description = "가맹점 ID", example = "101")
    private Long merchant_id;

    @NotNull
    @Schema(description = "결제 금액", example = "12000")
    private Long amount;

    @NotBlank
    @Schema(description = "주문명", example = "아메리카노 2잔")
    private String order_name;

    @NotBlank
    @Schema(description = "채널 타입", example = "ONLINE/OFFLINE")
    private String channel_type;
}
