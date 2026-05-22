package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantsConfirmRequest {

    private String split_method;
}
