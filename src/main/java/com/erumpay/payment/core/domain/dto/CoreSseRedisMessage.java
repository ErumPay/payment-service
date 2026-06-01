package com.erumpay.payment.core.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoreSseRedisMessage {

    private Long payment_id;
    private CoreSseEventResponse event;

    // [be] codex 260601 | Redis에는 payment_id와 표준화된 SSE payload를 함께 싣고 각 인스턴스가 동일한 이벤트를 전송한다.
    public static CoreSseRedisMessage of(Long paymentId, CoreSseEventResponse event) {
        return CoreSseRedisMessage.builder()
                .payment_id(paymentId)
                .event(event)
                .build();
    }
}
