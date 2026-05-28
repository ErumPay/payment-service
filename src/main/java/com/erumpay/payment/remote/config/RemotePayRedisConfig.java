package com.erumpay.payment.remote.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import com.erumpay.payment.remote.service.RemotePaySseRedisSubscriber;
import com.erumpay.payment.remote.service.RemotePaySseTopicProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class RemotePayRedisConfig {

    private final RemotePaySseTopicProperties remotePaySseTopicProperties;

    // [be] 영은 260528 1110 | 원격결제 상태 변경 Redis Pub/Sub 채널을 정의한다.
    @Bean
    public ChannelTopic remotePayRequestEventTopic() {
        return new ChannelTopic(remotePaySseTopicProperties.getRequestEvents());
    }

    // [be] 영은 260528 1110 | Redis 채널 메시지를 구독해 현재 인스턴스에 열린 원격결제 SSE 연결로 전달한다.
    @Bean
    public RedisMessageListenerContainer remotePayRedisMessageListenerContainer(
            RedisConnectionFactory redisConnectionFactory,
            @Qualifier("remotePayRequestEventTopic")
            ChannelTopic remotePayRequestEventTopic,
            RemotePaySseRedisSubscriber remotePaySseRedisSubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);
        container.setTaskExecutor(new SimpleAsyncTaskExecutor("remote-pay-redis-listener-"));
        container.setErrorHandler(error -> log.warn("RemotePay Redis listener error", error));
        container.setRecoveryInterval(5000L);
        container.addMessageListener(remotePaySseRedisSubscriber, remotePayRequestEventTopic);
        return container;
    }
}
