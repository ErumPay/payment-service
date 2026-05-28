package com.erumpay.payment.remote.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RemotePaySseEventResponse {

    private String event_type;
    private Long request_id;
    private RemotePayCreateResponse request;

    // [be] 영은 260528 1110 | 원격결제 SSE payload를 한 형태로 고정해 프론트가 connected/status-updated 이벤트를 같은 모델로 처리하게 한다.
    public static RemotePaySseEventResponse of(String eventType, RemotePayCreateResponse request) {
        return RemotePaySseEventResponse.builder()
                .event_type(eventType)
                .request_id(request.getRequest_id())
                .request(request)
                .build();
    }
}
