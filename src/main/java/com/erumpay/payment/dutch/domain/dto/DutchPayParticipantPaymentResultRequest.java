package com.erumpay.payment.dutch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Core가 참여자 결제 성공/실패 결과를 더치페이에 전달할 때 사용하는 내부 요청 DTO.
 *
 * <p>더치페이는 {@code user_id}와 {@code payment_id}로 세션 내 참여자를 찾아
 * 참여자 상태를 PAID 또는 실패 상태로 반영하고, 전체 참여자가 완료되면 세션 완료를 판단한다.</p>
 */
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
