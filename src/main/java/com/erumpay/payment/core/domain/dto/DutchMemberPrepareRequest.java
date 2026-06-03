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
public class DutchMemberPrepareRequest {

    @NotNull
    @Positive
    @Schema(description = "결제 금액", example = "5000")
    private Long amount;

    @NotNull
    @Positive
    @Schema(description = "더치페이 세션 ID", example = "1")
    private Long sessionId;

    @NotBlank
    @Schema(description = "주문명", example = "저녁 식사 정산")
    private String orderName;

    @NotNull
    @Positive
    @Schema(description = "가맹점 ID", example = "101")
    private Long merchantId;
}
