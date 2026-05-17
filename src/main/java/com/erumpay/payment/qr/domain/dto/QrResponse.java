package com.erumpay.payment.qr.domain.dto;

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
public class QrResponse {

    private String code;
    private Long amount;
    private String order_name;
    private String channel_type;
}
