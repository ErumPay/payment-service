package com.erumpay.payment.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class CoreSseTopicProperties {

    // [be] 다윤 260601 | 코어 결제 SSE 상태 변경 이벤트를 모든 payment-service 인스턴스에 전파하는 Redis 채널명
    @Value("${app.redis.channels.core-pay-events:core-pay:events}")
    private String paymentEvents;
}
