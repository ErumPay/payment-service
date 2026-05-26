package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeoutHandledSession {

        private Long session_id;
        private Long host_user_id;
        private Long host_auth_payment_id_to_void;
        private Long host_final_amount;
        private boolean host_final_payment_required;
    }
}
