package com.erumpay.payment.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.erumpay.payment.core.service.CoreSseRedisSubscriber;
import com.erumpay.payment.core.service.CoreSseTopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class CorePayRedisConfig {

    private final CoreSseTopicProperties coreSseTopicProperties;

    // [be] codex 260601 | 코어 결제 SSE 상태 변경 이벤트를 수신할 Redis Pub/Sub 채널
    @Bean
    public ChannelTopic corePayEventTopic() {
        return new ChannelTopic(coreSseTopicProperties.getPaymentEvents());
    }

    // [be] codex 260601 | Redis 채널 메시지를 구독해 현재 인스턴스의 코어 SSE 연결로 전달한다.
    @Bean
    public RedisMessageListenerContainer corePayRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            @Qualifier("corePayEventTopic") ChannelTopic corePayEventTopic,
            CoreSseRedisSubscriber coreSseRedisSubscriber) {

        log.info("RedisMessageListenerContainer corePayRedisMessageListenerContainer called.");
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(new SimpleAsyncTaskExecutor("core-pay-redis-listener-"));
        container.setErrorHandler(error -> log.warn("CorePay Redis listener error", error));
        container.setRecoveryInterval(5000L);
        container.addMessageListener(coreSseRedisSubscriber, corePayEventTopic);
        return container;
    }
}
