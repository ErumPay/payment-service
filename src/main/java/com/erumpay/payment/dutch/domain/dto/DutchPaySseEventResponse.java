package com.erumpay.payment.dutch.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DutchPaySseEventResponse {

    private String event_type;
    private Long session_id;
    private DutchPaySessionDetailResponse session;

    // [be] 영은 260523 1120 | SSE 이벤트명과 최신 세션 상세 응답을 하나의 payload로 묶는다
    public static DutchPaySseEventResponse of(
            String eventType,
            DutchPaySessionDetailResponse session) {
        return DutchPaySseEventResponse.builder()
                .event_type(eventType)
                .session_id(session.getSession_id())
                .session(session)
                .build();
    }
}
