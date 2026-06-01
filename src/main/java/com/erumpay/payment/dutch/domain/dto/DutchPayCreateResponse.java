package com.erumpay.payment.dutch.domain.dto;

import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 더치페이 세션 생성 응답 DTO.
 *
 * <p>Core가 대표자 가승인 결제 준비를 끝낸 뒤 더치페이 세션을 만들 때 받는 응답이다.
 * {@code host_auth_payment_id}는 대표자 auth-only 결제의 payment_id이며,
 * 이후 세션 완료 또는 타임아웃 정산 시 void 대상이 될 수 있다.</p>
 */
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
                .host_auth_payment_id(session.getHost_auth_payment_id())
                .total_amount(session.getTotal_amount())
                .status(session.getStatus().name())
                .build();
    }
}
