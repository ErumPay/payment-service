package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Core가 더치페이 참여자 결제 생성 전 검증을 요청할 때 사용하는 내부 요청 DTO.
 *
 * <p>참여자 ID, 사용자 ID, 결제 금액, 멱등키를 더치페이에 전달하면
 * 더치페이는 세션 상태와 참여자 부담 금액 기준으로 결제 가능 여부를 검증한다.</p>
 */
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantPaymentValidateRequest {

    private Long participant_id;
    private Long user_id;
    private Long amount;
    private String idempotency_key;
}
