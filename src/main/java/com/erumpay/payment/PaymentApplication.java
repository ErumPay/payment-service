package com.erumpay.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
@EnableFeignClients
public class PaymentApplication {

	public static void main(String[] args) {

		// Dotenv env = Dotenv.configure().ignoreIfMissing().load();
		Dotenv env = Dotenv.configure()
				.directory(System.getProperty("user.dir") + "/payment-service")
				.ignoreIfMissing()
				.load();
		env.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		System.out.println("DB_URL = " + System.getProperty("DB_URL"));

		System.out.println(">>> 실행 위치: " + System.getProperty("user.dir"));
		SpringApplication.run(PaymentApplication.class, args);
	}

}
