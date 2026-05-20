package com.erumpay.payment.core.domain.dto;

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
public class CoreRequest {

    @NotNull
    private Long paymentId;

    @NotNull
    private Long userId;

    @NotNull
    private Long amount;

    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String paymentType;

}
