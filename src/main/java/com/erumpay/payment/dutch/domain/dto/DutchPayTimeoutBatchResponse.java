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
 * 타임아웃 처리된 세션별 대표자 최종 부담금 정보를 담는다.</p>
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
     * <p>미결제 참여자 금액을 대표자 최종 부담금으로 계산해 대표자에게 안내하는 데 사용한다.
     * 실제 대표자 가승인 void는 대표자 최종 결제 완료 후 Core/PG 결제 흐름에서 처리한다.</p>
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

        /** 참여자 결제 완료분을 제외하고 대표자가 최종 부담해야 하는 금액. */
        private Long host_final_amount;

        /** 대표자 최종 결제가 추가로 필요한지 여부. */
        private boolean host_final_payment_required;
    }
}
