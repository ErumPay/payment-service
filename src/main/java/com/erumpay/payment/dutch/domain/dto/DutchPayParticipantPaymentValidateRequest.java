package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
