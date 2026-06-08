package com.erumpay.payment.core.domain.dto.request;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class PaymentAllFetchRequest {

    @NotNull
    @Schema(description = "조회 시작일", example = "2026-06-03")
    private LocalDate from;

    @NotNull
    @Schema(description = "조회 종료일", example = "2026-06-04")
    private LocalDate to;
}
