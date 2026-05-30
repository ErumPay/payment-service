package com.erumpay.payment.core.client.card;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.erumpay.payment.core.client.card.dto.CardBillingKeyResponse;

@FeignClient(name = "cardClient", url = "${card.base-url}")
public interface CardClient {

    @GetMapping("/internal/v1/cards/{cardId}/billing-key")
    CardBillingKeyResponse billingKeyLookUp(
            @PathVariable("cardId") Long cardId,
            @RequestParam("userId") Long userId);
}
