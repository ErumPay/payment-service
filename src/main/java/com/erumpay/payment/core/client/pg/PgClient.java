package com.erumpay.payment.core.client.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.erumpay.payment.core.client.pg.dto.PgAuthPayRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;

@FeignClient(name = "pgClient", url = "${pg.base-url}")
public interface PgClient {

        @PostMapping(value = "/internal/v1/pg/payments", consumes = "application/json")
        PgAuthPayResponse pgPaymentRequest(
                        @RequestHeader("Authorization") String authorization,
                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                        @RequestBody PgAuthPayRequest request);

        @PostMapping(value = "/internal/v1/pg/payments/auth-only", consumes = "application/json")
        PgAuthPayResponse pgPaymentAuthOnlyRequest(
                        @RequestHeader("Authorization") String authorization,
                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                        @RequestBody PgAuthPayRequest request);
}
