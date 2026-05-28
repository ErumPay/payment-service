package com.erumpay.payment.remote.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayPreparePaymentResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayRejectRequest;
import com.erumpay.payment.remote.service.RemotePayService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class RemotePayController {

    private final RemotePayService remotePayService;

    @PostMapping("/api/v1/remote-pay/requests")
    public ResponseEntity<RemotePayCreateResponse> createRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Controller");

        return ResponseEntity.ok(remotePayService.createRequest(userId, request));
    }

    @PostMapping("/api/v1/remote-pay/requests/{request_id}/prepare-payment")
    public ResponseEntity<RemotePayPreparePaymentResponse> preparePayment(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @PathVariable("request_id") @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{}/prepare-payment Controller", request_id);

        return ResponseEntity.ok(remotePayService.preparePayment(userId, idempotencyKey, request_id));
    }

    @PostMapping("/api/v1/remote-pay/requests/{request_id}/reject")
    public ResponseEntity<RemotePayCreateResponse> rejectRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id,
            @Valid @RequestBody(required = false) RemotePayRejectRequest request) {
        log.info("/api/v1/remote-pay/requests/{}/reject Controller", request_id);

        String rejectReason = request == null ? null : request.getReject_reason();
        return ResponseEntity.ok(remotePayService.rejectRequest(userId, request_id, rejectReason));
    }

    @PostMapping("/api/v1/remote-pay/requests/{request_id}/cancel")
    public ResponseEntity<RemotePayCreateResponse> cancelRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{}/cancel Controller", request_id);

        return ResponseEntity.ok(remotePayService.cancelRequest(userId, request_id));
    }
}
