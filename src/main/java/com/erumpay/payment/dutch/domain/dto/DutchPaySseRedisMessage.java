package com.erumpay.payment.dutch.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 더치페이 SSE 이벤트를 여러 서버 인스턴스에 전파하기 위한 Redis Pub/Sub 메시지 DTO.
 *
 * <p>Redis에는 세션 변경 사실만 싣고, 실제 화면 payload는 메시지를 받은 각 인스턴스가
 * DB에서 최신 세션 상태를 다시 조회해 만든다.</p>
 */
@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DutchPaySseRedisMessage {

    private Long session_id;
    private String event_type;

    public static DutchPaySseRedisMessage of(Long sessionId, String eventType) {
        return DutchPaySseRedisMessage.builder()
                .session_id(sessionId)
                .event_type(eventType)
                .build();
    }
}
