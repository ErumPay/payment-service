package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
