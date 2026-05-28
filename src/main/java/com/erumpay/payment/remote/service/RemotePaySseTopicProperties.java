package com.erumpay.payment.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class RemotePaySseTopicProperties {

    // [be] 영은 260528 1110 | 원격결제 상태 변경 이벤트를 모든 payment-service 인스턴스에 전파하는 Redis 채널명이다.
    @Value("${app.redis.channels.remote-pay-request-events:remote-pay:request-events}")
    private String requestEvents;
}
