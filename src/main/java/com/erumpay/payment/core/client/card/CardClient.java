package com.erumpay.payment.core.client.card;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.core.client.card.dto.CardBillingKeysRequest;
import com.erumpay.payment.core.client.card.dto.CardBillingKeysResponse;
import com.erumpay.payment.core.client.card.dto.MainCardResponse;
import com.erumpay.payment.core.client.card.dto.PaymentResultRequest;
import com.erumpay.payment.core.client.card.dto.PaymentResultResponse;

@FeignClient(name = "cardClient", url = "${card.base-url}")
public interface CardClient {

        @PostMapping("/internal/v1/cards/users/{userId}/billing-keys")
        CardBillingKeysResponse billingKeysLookUp(
                        @PathVariable("userId") Long userId,
                        @RequestBody CardBillingKeysRequest request);

        @PostMapping("/internal/v1/cards/users/{userId}/payment-usage-events")
        PaymentResultResponse paymentResultSend(
                        @PathVariable("userId") Long userId,
                        @RequestBody PaymentResultRequest request);

        @GetMapping("/internal/v1/cards/users/{userId}/default-card")
        MainCardResponse mainCardRequest(
                        @PathVariable("userId") Long userId);
}
