package com.erumpay.payment.core.domain.dto;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Schema(description = "결제 취소 응답")
public class CanceledResponse {

    @Schema(description = "결제 ID", example = "10001")
    private Long paymentId;
    @Schema(description = "결제 상태", example = "CANCELED")
    private String paymentStatus;
    @Schema(description = "취소 시각", example = "2026-05-31T23:59:59")
    private LocalDateTime canceledAt;

}
