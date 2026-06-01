package com.erumpay.payment.dutch.domain.dto;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

import lombok.Builder;
import lombok.Getter;

/**
 * 현재 사용자의 더치페이 결제 가능 여부 응답 DTO.
 *
 * <p>참여자가 결제 화면에 진입하기 전에 본인 부담 금액, 세션 상태,
 * 기존 payment_id 연결 여부를 확인하는 데 사용한다.</p>
 */
@Builder
@Getter
public class DutchPayMyPaymentResponse {

    private Long session_id;
    private Long participant_id;
    private Long user_id;
    private Long host_user_id;
    private Long merchant_id;
    private String order_name;
    private Long amount;
    private Long total_amount;
    private String split_method;
    private String session_status;
    private String participant_status;
    private Long payment_id;
    private boolean payable;

    public static DutchPayMyPaymentResponse fromEntity(
            DutchPaySessionEntity session,
            DutchPayParticipantEntity participant) {
        Long paymentId = participant.getPayment() == null ? null : participant.getPayment().getPaymentId();
        boolean payable = session.getStatus() == DutchPaySessionEntity.DutchPayStatus.IN_PROGRESS
                && participant.getStatus() == DutchPayParticipantEntity.ParticipantStatus.PENDING
                && participant.getAmount() != null
                && participant.getAmount() > 0
                && paymentId == null;

        return DutchPayMyPaymentResponse.builder()
                .session_id(session.getSession_id())
                .participant_id(participant.getParticipant_id())
                .user_id(participant.getUser_id())
                .host_user_id(session.getHost_user_id())
                .merchant_id(session.getMerchant_id())
                .order_name(session.getOrder_name())
                .amount(participant.getAmount())
                .total_amount(session.getTotal_amount())
                .split_method(session.getSplit_method().name())
                .session_status(session.getStatus().name())
                .participant_status(participant.getStatus().name())
                .payment_id(paymentId)
                .payable(payable)
                .build();
    }
}
