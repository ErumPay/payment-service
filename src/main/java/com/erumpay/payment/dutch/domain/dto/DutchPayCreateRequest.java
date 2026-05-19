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
public class DutchPayCreateRequest {

    private Long host_user_id;
    private Long merchant_id;
    private Long total_amount;
    private String order_name;
}
