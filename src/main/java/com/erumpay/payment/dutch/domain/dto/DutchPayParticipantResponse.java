package com.erumpay.payment.dutch.domain.dto;

import java.util.Objects;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * 더치페이 세션 상세에 포함되는 참여자별 응답 DTO.
 *
 * <p>프론트는 이 값으로 참여자별 결제 상태, 부담 금액, 결제 연결 여부,
 * 대표자 여부를 표시한다. {@code payment_id}는 참여자 결제가 생성된 뒤에만 내려간다.</p>
 */
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
