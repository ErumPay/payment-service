package com.erumpay.payment.qr.domain.dto;

import com.erumpay.payment.core.domain.entity.OrderEntity;

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

    public static QrResponse fromOrderEntity(OrderEntity entity, String code) {
        return QrResponse.builder()
                .code(code)
                .amount(entity.getAmount())
                .order_name(entity.getOrder_name())
                .channel_type(entity.getChannel_type().name())
                .build();
    }
}
