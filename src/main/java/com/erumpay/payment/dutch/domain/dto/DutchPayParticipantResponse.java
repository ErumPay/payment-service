package com.erumpay.payment.dutch.domain.dto;

import java.util.Objects;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DutchPayParticipantResponse {

    private Long participant_id;
    private Long user_id;
    private Long amount;
    private Long payment_id;
    private String status;
    private boolean host;

    public static DutchPayParticipantResponse fromEntity(
            DutchPayParticipantEntity participant,
            Long hostUserId) {
        return DutchPayParticipantResponse.builder()
                .participant_id(participant.getParticipant_id())
                .user_id(participant.getUser_id())
                .amount(participant.getAmount())
                .payment_id(participant.getPayment() == null ? null : participant.getPayment().getPaymentId())
                .status(participant.getStatus().name())
                .host(Objects.equals(participant.getUser_id(), hostUserId))
                .build();
    }
}
