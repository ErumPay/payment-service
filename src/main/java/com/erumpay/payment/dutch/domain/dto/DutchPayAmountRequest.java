package com.erumpay.payment.dutch.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 더치페이 참여자가 본인 부담 금액을 입력할 때 사용하는 요청 DTO.
 *
 * <p>CUSTOM 분배 방식에서 참여자가 직접 금액을 입력하면 이 값이 참여자 amount로 반영된다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayAmountRequest {

    @NotNull
    @Positive
    private Long amount;
}
