package com.erumpay.payment.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class PinAndPayResponse {

    private Long paymentId;
    private Long userId;
    private String paymentStatus;
    private String paymentType;

}
