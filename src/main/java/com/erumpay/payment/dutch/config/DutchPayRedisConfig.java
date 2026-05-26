package com.erumpay.payment.dutch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.erumpay.payment.dutch.service.DutchPaySseRedisSubscriber;
import com.erumpay.payment.dutch.service.DutchPaySseTopicProperties;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class DutchPayRedisConfig {

    private final DutchPaySseTopicProperties dutchPaySseTopicProperties;

    // [be] 영은 260526 1020 | 더치페이 SSE 이벤트를 수신할 Redis Pub/Sub 채널을 정의한다
    @Bean
    public ChannelTopic dutchPaySessionEventTopic() {
        return new ChannelTopic(dutchPaySseTopicProperties.getSessionEvents());
    }

    // [be] 영은 260526 1020 | Redis 채널 메시지를 구독해 각 인스턴스의 로컬 SSE 연결로 전달한다
    @Bean
    public RedisMessageListenerContainer dutchPayRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            ChannelTopic dutchPaySessionEventTopic,
            DutchPaySseRedisSubscriber dutchPaySseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.addMessageListener(dutchPaySseRedisSubscriber, dutchPaySessionEventTopic);
        return container;
    }
}
