package com.erumpay.payment.dutch.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class DutchPaySseTopicProperties {

    // [be] 영은 260526 1020 | 더치페이 세션 변경 이벤트를 모든 payment-service 인스턴스에 전파하는 Redis 채널명
    @Value("${app.redis.channels.dutch-pay-session-events:dutch-pay:session-events}")
    private String sessionEvents;
}
