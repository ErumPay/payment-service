package com.erumpay.payment.core.client.card;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.core.client.card.dto.CardBillingKeysRequest;
import com.erumpay.payment.core.client.card.dto.CardBillingKeysResponse;

@FeignClient(name = "cardClient", url = "${card.base-url}")
public interface CardClient {

    @PostMapping("/internal/v1/cards/users/{userId}/billing-keys")
    CardBillingKeysResponse billingKeysLookUp(
            @PathVariable("userId") Long userId,
            @RequestBody CardBillingKeysRequest request);
}
