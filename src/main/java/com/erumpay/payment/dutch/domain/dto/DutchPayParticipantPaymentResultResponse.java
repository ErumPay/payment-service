package com.erumpay.payment.dutch.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Core가 참여자 결제 결과를 더치페이에 반영한 뒤 받는 내부 응답 DTO.
 *
 * <p>마지막 참여자 결제로 세션이 COMPLETED가 되면 대표자 auth-only 가승인을 void 해야 하므로,
 * Core가 이어서 취소/void 흐름을 호출할 수 있도록 대상 payment_id와 멱등키를 함께 내려준다.</p>
 */
@Builder
@Getter
public class DutchPayParticipantPaymentResultResponse {

    private Long session_id;
    private String status;
    private DutchPaySessionDetailResponse session;

    /** 세션 완료 시 Core가 void 처리해야 하는 대표자 auth-only payment_id. */
    private Long host_auth_payment_id_to_void;

    /** 대표자 가승인 void가 필요한 응답인지 여부. */
    private boolean host_auth_void_required;

    /** Core가 void 요청 멱등성 보장에 사용할 수 있는 서버 생성 멱등키. */
    private String host_auth_void_idempotency_key;

    /** void가 필요한 이유. 예: DUTCH_COMPLETED. */
    private String host_auth_void_reason;
}
