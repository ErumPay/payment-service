package com.erumpay.payment.core.domain.dto;

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
    private Long amount;

    @NotNull
    @Positive
    private Long sessionId;

    @NotBlank
    private String orderName;

    @NotNull
    @Positive
    private Long merchantId;

}
