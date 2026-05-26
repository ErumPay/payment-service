package com.erumpay.payment.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.domain.dto.PrepareRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.DutchMemberPrepareRequest;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.service.CoreSseService;
import com.erumpay.payment.core.service.CoreService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class CoreController {

    private final CoreService coreService;
    private final CoreSseService coreSseService;

    // [be] 다윤 260522 개인+대표자 결제 요청
    @PostMapping("/prepare")
    public ResponseEntity<PrepareResponse> prepare(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PrepareRequest request) {

        log.info("/payment/prepare Controller");

        return coreService.prepare(userId, idempotencyKey, request);
    }

    // [be] 다윤 260522 참여자 결제 요청
    @PostMapping("/prepare-member")
    public ResponseEntity<PrepareResponse> prepareMember(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DutchMemberPrepareRequest request) {

        log.info("/payment/prepare-member Controller");

        return coreService.prepareMember(userId, idempotencyKey, request);
    }

    // [be] 다윤 260526 결제 SSE 연결
    @GetMapping("/{paymentId}/subscribe")
    public SseEmitter sseStream(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long paymentId) {
        if (!coreService.userCanAccess(paymentId, userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        log.info("/payment/{}/subscribe SSE subscribe", paymentId);
        return coreSseService.subscribe(paymentId);
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    @PostMapping("/request")
    public ResponseEntity<PinAndPayResponse> request(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PinAndPayRequest request) {

        log.info("/payment/request controller");

        return coreService.request(userId, idempotencyKey, request);
    }

}
