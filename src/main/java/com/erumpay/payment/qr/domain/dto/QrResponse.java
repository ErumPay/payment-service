package com.erumpay.payment.qr.domain.dto;

import com.erumpay.payment.core.domain.entity.CoreEntity;

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

    private Long paymentId;
    private String code;
    private Long amount;
    private String order_name;
    private String channel_type;

    public static QrResponse fromOrderEntity(CoreEntity entity, String code) {
        return QrResponse.builder()
                .code(code)
                .paymentId(entity.getPayment_id())
                .amount(entity.getAmount())
                .order_name(entity.getOrder_name())
                .channel_type(entity.getChannel_type().name())
                .build();
    }
}
