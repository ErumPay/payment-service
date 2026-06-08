package com.erumpay.payment.dutch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Core가 대표자 최종 결제 성공 결과를 Dutch에 전달할 때 사용하는 내부 요청 DTO.
 *
 * <p>참여자 결제 또는 타임아웃 정산 이후 대표자가 본인 최종 부담금을 결제하면,
 * Core는 이 요청으로 Dutch 세션을 COMPLETED로 전환시킨다.</p>
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DutchPayHostFinalPaymentResultRequest {

    @NotNull
    @Positive
    private Long user_id;

    @NotNull
    @Positive
    private Long payment_id;

    @NotBlank
    private String status;
}
