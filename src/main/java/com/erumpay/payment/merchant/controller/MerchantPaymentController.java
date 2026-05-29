package com.erumpay.payment.merchant.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.merchant.domain.dto.MerchantCancelResponse;
import com.erumpay.payment.merchant.domain.dto.MerchantPaymentRequest;
import com.erumpay.payment.merchant.domain.dto.MerchantPaymentResponse;
import com.erumpay.payment.merchant.service.MerchantApiKeyResolver;
import com.erumpay.payment.merchant.service.MerchantPaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/merchant/payments")
@RequiredArgsConstructor
public class MerchantPaymentController {

    private final MerchantPaymentService merchantPaymentService;
    private final MerchantApiKeyResolver merchantApiKeyResolver;

    // [be] 나영은 260529 1638 | 오픈 SDK가 호출하는 가맹점 결제 생성 API. 기존 QR 이미지 생성 API와 별도 JSON 계약을 제공한다.
    @PostMapping
    public ResponseEntity<MerchantPaymentResponse> create(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody MerchantPaymentRequest request) {

        Long merchantId = merchantApiKeyResolver.resolveMerchantId(authorization);
        return ResponseEntity.ok(merchantPaymentService.create(merchantId, idempotencyKey, request));
    }

    // [be] 나영은 260529 1638 | 오픈 SDK가 결제 상태를 확인할 때 사용하는 가맹점 소유 결제 단건 조회 API.
    @GetMapping("/{paymentId}")
    public ResponseEntity<MerchantPaymentResponse> get(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable("paymentId") Long paymentId) {

        Long merchantId = merchantApiKeyResolver.resolveMerchantId(authorization);
        return ResponseEntity.ok(merchantPaymentService.get(merchantId, paymentId));
    }

    // [be] 나영은 260529 1638 | 오픈 SDK용 가맹점 결제 취소 API. 사용자 X-User-Id 흐름과 권한 기준을 분리한다.
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<MerchantCancelResponse> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("paymentId") Long paymentId) {

        Long merchantId = merchantApiKeyResolver.resolveMerchantId(authorization);
        return ResponseEntity.ok(merchantPaymentService.cancel(merchantId, idempotencyKey, paymentId));
    }
}
