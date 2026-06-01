package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Core가 대표자 가승인 성공/실패 결과를 더치페이에 전달할 때 사용하는 내부 요청 DTO.
 *
 * <p>{@code status}는 Core/PG에서 확정한 가승인 결과이며, 현재 더치페이는
 * {@code AUTHORIZED}만 성공으로 보고 그 외 값은 대표자 가승인 실패로 처리한다.</p>
 */
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayHostAuthorizationResultRequest {

    private Long payment_id;
    private String status;
    private String fail_code;
}
