package com.erumpay.payment.core.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
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
public class DirectPinAndPayRequest {

    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "pin must be 6 digits")
    @Schema(description = "결제 PIN 6자리", example = "123456")
    private String pin;

    @NotNull
    @Schema(description = "결제 ID", example = "1")
    private Long paymentId;

    @NotNull
    @Positive
    @Schema(description = "총 결제 금액", example = "10000")
    private Long totalAmount;

    @NotNull
    @Positive
    @Schema(description = "직접 선택한 카드 ID", example = "1")
    private Long cardId;
}
