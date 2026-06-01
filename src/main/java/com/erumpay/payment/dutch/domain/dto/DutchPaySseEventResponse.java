package com.erumpay.payment.dutch.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 더치페이 SSE로 내려가는 화면 갱신 이벤트 응답 DTO.
 *
 * <p>이벤트명과 최신 세션 상세 payload를 함께 내려서 프론트가 세션 상세 화면과
 * 프로그레스바를 같은 데이터 기준으로 갱신하도록 한다.</p>
 */
@Builder
@Getter
public class DutchPaySseEventResponse {

    private String event_type;
    private Long session_id;
    private DutchPaySessionDetailResponse session;

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
