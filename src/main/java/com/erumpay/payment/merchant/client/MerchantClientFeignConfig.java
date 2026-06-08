package com.erumpay.payment.merchant.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import feign.Retryer;

public class MerchantClientFeignConfig {

    @Bean
    Retryer merchantClientRetryer(
            @Value("${spring.cloud.openfeign.client.config.merchantClient.retry.period:200}") long period,
            @Value("${spring.cloud.openfeign.client.config.merchantClient.retry.maxPeriod:1000}") long maxPeriod,
            @Value("${spring.cloud.openfeign.client.config.merchantClient.retry.maxAttempts:3}") int maxAttempts) {
        return new Retryer.Default(period, maxPeriod, maxAttempts);
    }
}
