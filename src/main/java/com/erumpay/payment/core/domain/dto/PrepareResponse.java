package com.erumpay.payment.core.domain.dto;

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
public class PrepareResponse {

    private Long paymentId;
    private String paymentStatus;
    private String recommendationStatus;
    private String paymentType;
    private String paymentIntent;
    private String dutchRole;
    private Long dutchSessionId;
    private Long amount;

}
