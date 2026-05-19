package com.erumpay.payment.dutch.domain.dto;

import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayCreateResponse {

    private String code;
    private Long session_id;
    private String dutch_order_no;
    private Long host_user_id;
    private Long merchant_id;
    private String order_name;
    private Long host_auth_payment_id;
    private Long total_amount;
    private String status;

    public static DutchPayCreateResponse fromEntity(DutchPaySessionEntity session, String code) {
        return DutchPayCreateResponse.builder()
                .code(code)
                .session_id(session.getSession_id())
                .dutch_order_no(session.getDutch_order_no())
                .host_user_id(session.getHost_user_id())
                .merchant_id(session.getMerchant_id())
                .order_name(session.getOrder_name())
                .host_auth_payment_id(session.getHost_auth_payment() == null
                        ? null
                        : session.getHost_auth_payment().getPayment_id())
                .total_amount(session.getTotal_amount())
                .status(session.getStatus().name())
                .build();
    }
}
