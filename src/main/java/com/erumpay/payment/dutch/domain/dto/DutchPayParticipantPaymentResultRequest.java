package com.erumpay.payment.dutch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DutchPayParticipantPaymentResultRequest {

    @NotNull
    @Positive
    private Long user_id;

    @NotNull
    @Positive
    private Long payment_id;

    @NotBlank
    private String status;
}
