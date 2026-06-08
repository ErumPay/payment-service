package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Core가 더치페이 참여자 결제 생성 전 검증을 요청할 때 사용하는 내부 요청 DTO.
 *
 * <p>Core는 더치 내부 PK인 participant_id를 알 필요가 없고,
 * path의 session_id와 요청의 user_id로 참여자 row를 식별한다.</p>
 */
@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantPaymentValidateRequest {

    private Long user_id;
    private Long amount;
    private String idempotency_key;
}
