package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 더치페이 참여자 결제 생성 전 검증 결과 응답 DTO.
 *
 * <p>Core는 이 응답으로 참여자가 결제 가능한 상태인지 확인한 뒤
 * 실제 payment_orders 생성 또는 결제 준비 흐름을 이어간다.</p>
 */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantPaymentValidateResponse {

    private boolean valid;
    private Long session_id;
    private Long participant_id;
    private Long user_id;
    private Long amount;
    private String participant_status;

    public static DutchPayParticipantPaymentValidateResponse valid(
            Long sessionId,
            Long participantId,
            Long userId,
            Long amount,
            String participantStatus) {
        return DutchPayParticipantPaymentValidateResponse.builder()
                .valid(true)
                .session_id(sessionId)
                .participant_id(participantId)
                .user_id(userId)
                .amount(amount)
                .participant_status(participantStatus)
                .build();
    }
}
