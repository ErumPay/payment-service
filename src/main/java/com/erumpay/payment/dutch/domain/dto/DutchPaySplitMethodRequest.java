package com.erumpay.payment.dutch.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 대표자가 더치페이 금액 분배 방식을 설정할 때 사용하는 요청 DTO.
 *
 * <p>{@code EQUAL}이면 서버가 균등 분배 금액을 계산하고,
 * {@code CUSTOM}이면 참여자별 금액 입력 단계로 진행한다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPaySplitMethodRequest {

    @NotBlank
    private String split_method;
}
