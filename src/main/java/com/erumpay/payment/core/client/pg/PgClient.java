package com.erumpay.payment.core.client.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import com.erumpay.payment.core.client.pg.dto.PgAuthRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthResponse;

@FeignClient(name = "pgClient", url = "${pg.base-url}")
public interface PgClient {

    @PostMapping(
            value = "/internal/v1/pg/payments",
            consumes = "application/json")
    PgAuthResponse pgPaymentRequest(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PgAuthRequest request);

}
