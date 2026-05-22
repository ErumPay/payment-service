package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DutchPaySessionDetailResponse {

    private Long session_id;
    private String dutch_order_no;
    private Long host_user_id;
    private Long merchant_id;
    private String order_name;
    private Long host_auth_payment_id;
    private Long total_amount;
    private Long remaining_amount;
    private String split_method;
    private String status;
    private List<DutchPayParticipantResponse> participants;

    public static DutchPaySessionDetailResponse fromEntity(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        Long assignedAmount = participants.stream()
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);

        return DutchPaySessionDetailResponse.builder()
                .session_id(session.getSession_id())
                .dutch_order_no(session.getDutch_order_no())
                .host_user_id(session.getHost_user_id())
                .merchant_id(session.getMerchant_id())
                .order_name(session.getOrder_name())
                .host_auth_payment_id(session.getHost_auth_payment_id())
                .total_amount(session.getTotal_amount())
                .remaining_amount(session.getTotal_amount() - assignedAmount)
                .split_method(session.getSplit_method().name())
                .status(session.getStatus().name())
                .participants(participants.stream()
                        .map(participant -> DutchPayParticipantResponse.fromEntity(participant, session.getHost_user_id()))
                        .toList())
                .build();
    }
}
