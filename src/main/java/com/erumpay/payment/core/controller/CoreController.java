package com.erumpay.payment.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.domain.dto.PrepareRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.CanceledResponse;
import com.erumpay.payment.core.domain.dto.DutchMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.PaymentDetailResponse;
import com.erumpay.payment.core.domain.dto.PaymentListResonse;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Core Payment", description = "결제 Core API")
public class CoreController {

    private final CoreService coreService;
    private final CoreSseService coreSseService;

    // [be] 다윤 260522 개인 + 대표자 가승인 + 원격 결제 요청
    @PostMapping("/prepare")
    @Operation(summary = "결제 생성 요청", description = "단일결제 또는 대표자 가승인 결제의 생성을 요청한다.")
    public ResponseEntity<PrepareResponse> preparePay(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PrepareRequest request) {

        log.info("/payment/prepare Controller");

        return coreService.preparePay(userId, idempotencyKey, request);
    }

    // [be] 다윤 260522 참여자 결제 요청
    @PostMapping("/prepare-member")
    @Operation(summary = "참여자 결제 생성 요청", description = "더치페이 참여자의 결제의 생성을 요청한다.")
    public ResponseEntity<PrepareResponse> prepareMember(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DutchMemberPrepareRequest request) {

        log.info("/payment/prepare-member Controller");

        return coreService.prepareMember(userId, idempotencyKey, request);
    }

    // [be] 다윤 260528 01:00 | 대표자 실결제 요청
    @PostMapping("/prepare-host")
    @Operation(summary = "대표자 실결제 생성 요청", description = "더치페이 대표자의 실결제의 결제의 생성을 요청한다.")
    public ResponseEntity<PrepareResponse> prepareHost(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DutchMemberPrepareRequest request) {

        log.info("/payment/prepare-host Controller");

        return coreService.prepareHost(userId, idempotencyKey, request);
    }

    // [be] 다윤 260526 결제 SSE 연결
    @GetMapping("/{paymentId}/subscribe")
    @Operation(summary = "결제 상태 SSE 구독", description = "결제 상태 변경 이벤트를 SSE로 구독한다. 이벤트명은 connected/payment-updated를 사용한다.")
    public SseEmitter sseStream(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "결제 ID", required = true) @PathVariable("paymentId") Long paymentId) {
        if (!coreService.userCanAccess(paymentId, userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        log.info("/payment/{}/subscribe SSE subscribe", paymentId);
        return coreSseService.subscribe(paymentId);
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    @PostMapping("/request")
    @Operation(summary = "비밀번호 확인 후 결제 요청", description = "결제 PIN을 검증하고 실제 결제를 요청한다.")
    public ResponseEntity<PinAndPayResponse> requestPay(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PinAndPayRequest request) {

        log.info("/payment/request controller");

        return coreService.requestPay(userId, idempotencyKey, request);
    }

    // [be] 다윤 260527 결제 취소 요청
    @PostMapping("/{paymentId}/cancel")
    @Operation(summary = "결제 취소 요청", description = "승인된 결제를 취소한다.")
    public ResponseEntity<CanceledResponse> cancelPay(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "결제 ID", required = true) @PathVariable("paymentId") Long paymentId) {

        log.info("/payment/cancel Controller");

        return ResponseEntity.ok(coreService.cancelPay(userId, idempotencyKey, paymentId));
    }

    @GetMapping
    @Operation(summary = "결제 내역 전체 조회", description = "결제된 내역 전체를 조회한다. status = (ALL, PAID, CANCELED)")
    public ResponseEntity<PaymentListResonse> getAllPayments(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "조회할 페이지 번호(start = 0)", required = false, example = "0") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "조회할 결제 상태(ALL, PAID, CANCELED)", required = false, example = "ALL") @RequestParam(name = "status", defaultValue = "ALL") String status) {
        return ResponseEntity.ok(coreService.getAllPayments(userId, page, status));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "특정 결제 내역 조회", description = "결제된 내역 한 개를 조회한다.")
    public ResponseEntity<PaymentDetailResponse> getDetailPayment(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "결제 ID", required = true) @PathVariable("paymentId") Long paymentId) {

        return ResponseEntity.ok(coreService.getDetailPayment(userId, paymentId));
    }

}
