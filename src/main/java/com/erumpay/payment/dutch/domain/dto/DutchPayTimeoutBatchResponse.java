package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 더치페이 타임아웃 배치 실행 결과 응답 DTO.
 *
 * <p>15분/25분 경고 발송 건수, 30분 타임아웃 처리 건수, 실패한 세션 목록,
 * 타임아웃 처리된 세션별 대표자 최종 부담금과 대표자 가승인 void 대상 payment_id를 담는다.</p>
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DutchPayTimeoutBatchResponse {

    private int warning_1_count;
    private int warning_2_count;
    private int timeout_handled_count;
    private int failed_count;
    private List<Long> failed_session_ids;
    private List<TimeoutHandledSession> timeout_sessions;

    /**
     * 30분 타임아웃으로 정리된 단일 더치페이 세션 결과.
     *
     * <p>{@code host_auth_payment_id_to_void}는 DB 컬럼이 아니라 응답용 파생 필드다.
     * DB에 저장된 {@code dutch_pay_sessions.host_auth_payment_id}를 가져와
     * Core가 대표자 auth-only 가승인을 void 처리할 때 사용할 payment_id로 내려준다.</p>
     */
    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeoutHandledSession {

        /** 타임아웃 처리된 더치페이 세션 ID. */
        private Long session_id;

        /** 최종 미결제분을 부담해야 하는 대표자 user_id. */
        private Long host_user_id;

        /** Core가 void 처리해야 하는 대표자 auth-only payment_id. */
        private Long host_auth_payment_id_to_void;

        /** Core가 void 요청 멱등성 보장에 사용할 수 있는 서버 생성 멱등키. */
        private String host_auth_void_idempotency_key;

        /** void가 필요한 이유. 예: DUTCH_TIMEOUT. */
        private String host_auth_void_reason;

        /** 참여자 결제 완료분을 제외하고 대표자가 최종 부담해야 하는 금액. */
        private Long host_final_amount;

        /** 대표자 최종 결제가 추가로 필요한지 여부. */
        private boolean host_final_payment_required;
    }
}
