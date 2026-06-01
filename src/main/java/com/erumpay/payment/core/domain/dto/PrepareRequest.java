package com.erumpay.payment.core.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class PrepareRequest {

    @NotNull
    @Schema(description = "결제 ID", example = "10001")
    private Long paymentId;

    @NotNull
    @Positive
    @Schema(description = "결제 금액", example = "15000")
    private Long amount;

    @NotBlank
    @Schema(description = "결제 타입", example = "SINGLE/DUTCH/REMOTE")
    private String paymentType;

    @Schema(description = "더치 결제 역할 (더치 결제 시)", example = "HOST/MEMBER")
    private String dutchRole;

}
