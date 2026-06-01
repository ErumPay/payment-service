package com.erumpay.payment.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "결제 사전 승인 응답")
public class PrepareResponse {

    @Schema(description = "결제 ID", example = "10001")
    private Long paymentId;
    @Schema(description = "결제 상태", example = "PAY_PENDING")
    private String paymentStatus;
    @Schema(description = "추천 상태", example = "PENDING")
    private String recommendationStatus;
    @Schema(description = "결제 타입", example = "SINGLE")
    private String paymentType;
    @Schema(description = "결제 의도", example = "DUTCH_HOST_PAY")
    private String paymentIntent;
    @Schema(description = "더치 역할", example = "HOST")
    private String dutchRole;
    @Schema(description = "더치 세션 ID", example = "3001")
    private Long dutchSessionId;
    @Schema(description = "결제 금액", example = "15000")
    private Long amount;

}
