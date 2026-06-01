package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Core가 더치페이 세션 생성을 요청할 때 사용하는 내부 요청 DTO.
 *
 * <p>대표자 가승인 결제의 {@code host_payment_id}와 주문 정보를 받아
 * 더치페이 세션 및 대표자 참여자 row를 생성한다. 프론트가 직접 호출하는 요청이 아니다.</p>
 */
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayCreateRequest {

    private Long host_payment_id;
    private Long host_user_id;
    private Long merchant_id;
    private Long total_amount;
    private String order_name;
}
