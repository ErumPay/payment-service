package com.erumpay.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:payment_service_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.datasource.driver-class-name=org.h2.Driver",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
	"spring.qr.baseUrl=http://localhost:8083/qr?token=",
	"auth.base-url=http://localhost:8081",
	"pg.base-url=http://localhost:8093",
	"recommend.base-url=http://localhost:8084",
	"card.base-url=http://localhost:8082"
})
class PaymentApplicationTests {

	@MockitoBean(name = "corePayRedisMessageListenerContainer")
	private RedisMessageListenerContainer corePayRedisMessageListenerContainer;

	@MockitoBean(name = "dutchPayRedisMessageListenerContainer")
	private RedisMessageListenerContainer dutchPayRedisMessageListenerContainer;

	@MockitoBean(name = "remotePayRedisMessageListenerContainer")
	private RedisMessageListenerContainer remotePayRedisMessageListenerContainer;

	@Test
	void contextLoads() {
	}

}
