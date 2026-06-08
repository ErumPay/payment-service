package com.erumpay.payment.core.domain.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PinAndPayRequest {
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "pin must be 6 digits")
    @Schema(description = "결제 PIN 6자리", example = "123456")
    private String pin;

    @NotNull
    @Schema(description = "결제 ID", example = "1")
    private Long paymentId;

    @NotNull
    @Positive
    @Schema(description = "총 결제 금액(card.amount의 합과 일치해야 함)", example = "10000")
    private Long totalAmount;

    @NotBlank
    @Pattern(regexp = "^(BENEFIT_SINGLE|PERF_SINGLE|BENEFIT_SPLIT|PERF_SPLIT)$", message = "strategyType must be one of BENEFIT_SINGLE, PERF_SINGLE, BENEFIT_SPLIT, PERF_SPLIT")
    @Schema(description = "선택한 추천 전략", example = "BENEFIT_SINGLE", allowableValues = {
            "BENEFIT_SINGLE",
            "PERF_SINGLE",
            "BENEFIT_SPLIT",
            "PERF_SPLIT"
    })
    private String strategyType;

    @NotEmpty
    @Schema(description = "카드 분할 결제 목록", example = "[{\"cardId\":1,\"amount\":10000}]")
    private List<CardPortion> cards;

    @Getter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class CardPortion {

        @NotNull
        @Positive
        @Schema(description = "카드 ID", example = "1")
        private Long cardId;

        @NotNull
        @Positive
        @Schema(description = "해당 카드 결제 금액", example = "10000")
        private Long amount;
    }
}
