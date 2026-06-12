package com.erumpay.payment.dutch.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    private Long host_payment_id;
    private Long host_user_id;
    private Long merchant_id;
    private Long total_amount;
    @JsonProperty("merchant_name")
    private String merchant_name;
}
