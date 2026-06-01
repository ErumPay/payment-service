package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 대표자가 더치페이 참여 인원을 확정할 때 사용하는 요청 DTO.
 *
 * <p>확정 시점의 분배 방식을 함께 전달하며, 이후 금액 입력 또는 결제 요청 단계로 넘어간다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantsConfirmRequest {

    private String split_method;
}
