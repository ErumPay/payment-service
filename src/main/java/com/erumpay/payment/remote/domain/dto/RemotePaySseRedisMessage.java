package com.erumpay.payment.remote.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RemotePaySseRedisMessage {

    private Long request_id;
    private String event_type;

    // [be] 영은 260528 1110 | Redis에는 변경된 원격결제 request_id와 이벤트명만 싣고, 각 인스턴스가 최신 DB 상태를 다시 읽는다.
    public static RemotePaySseRedisMessage of(Long requestId, String eventType) {
        return RemotePaySseRedisMessage.builder()
                .request_id(requestId)
                .event_type(eventType)
                .build();
    }
}
