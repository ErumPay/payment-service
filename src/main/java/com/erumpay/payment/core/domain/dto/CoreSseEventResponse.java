package com.erumpay.payment.core.domain.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreSseEventResponse {

    private CoreSseEventType eventType;
    private Long paymentId;
    private Object payload;
    private LocalDateTime occurredAt;

    public static CoreSseEventResponse of(CoreSseEventType eventType, Long paymentId, Object payload) {
        return CoreSseEventResponse.builder()
                .eventType(eventType)
                .paymentId(paymentId)
                .payload(payload)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
