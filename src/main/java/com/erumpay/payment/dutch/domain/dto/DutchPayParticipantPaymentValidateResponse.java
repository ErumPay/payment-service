package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Core의 더치페이 참여자 결제 생성 전 검증 결과 응답 DTO.
 *
 * <p>Core가 후속 결제 주문을 생성하는 데 필요한 검증 결과, 사용자, 금액, 참여자 상태만 내려준다.
 * 더치 내부 PK인 participant_id는 Core 경계 밖으로 전달하지 않는다.</p>
 */
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantPaymentValidateResponse {

    private boolean valid;
    private Long session_id;
    private Long user_id;
    private Long amount;
    private String participant_status;

    public static DutchPayParticipantPaymentValidateResponse valid(
            Long sessionId,
            Long userId,
            Long amount,
            String participantStatus) {
        return DutchPayParticipantPaymentValidateResponse.builder()
                .valid(true)
                .session_id(sessionId)
                .user_id(userId)
                .amount(amount)
                .participant_status(participantStatus)
                .build();
    }
}
