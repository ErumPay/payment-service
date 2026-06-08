package com.erumpay.payment.core.client.recommend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RecommendRequest {

    private Long paymentId;
    private Long userId;
    private String merchantName;
    private String mccCode;
    private Long amount;

}
