package com.erumpay.payment.remote.domain.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RemotePayExpireBatchResponse {

    private int expired_count;
    private int failed_count;
    private List<Long> failed_request_ids;
    private List<ExpiredRequest> expired_requests;

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpiredRequest {

        private Long request_id;
        private Long requester_user_id;
        private Long target_user_id;
        private Long payment_id;
    }
}
