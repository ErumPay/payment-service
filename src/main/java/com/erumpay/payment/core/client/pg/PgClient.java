package com.erumpay.payment.core.client.pg;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.core.client.pg.dto.PgAuthRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthResponse;

@FeignClient(name = "pgClient", url = "${pg.base-url}")
public interface PgClient {

    @PostMapping("/internal/v1/pg/payments")
    PgAuthResponse pgPaymentRequest(
            @RequestBody PgAuthRequest request);

}
