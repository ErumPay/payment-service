package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DutchPaySseRedisMessage {

    private Long session_id;
    private String event_type;

    // [be] 영은 260526 1020 | Redis에는 세션 변경 사실만 싣고 실제 화면 payload는 각 인스턴스가 최신 DB 상태로 만든다
    public static DutchPaySseRedisMessage of(Long sessionId, String eventType) {
        return DutchPaySseRedisMessage.builder()
                .session_id(sessionId)
                .event_type(eventType)
                .build();
    }
}
