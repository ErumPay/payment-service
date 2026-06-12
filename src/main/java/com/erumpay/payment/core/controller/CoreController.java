package com.erumpay.payment.core.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.domain.dto.request.DutchMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.request.DirectPinAndPayRequest;
import com.erumpay.payment.core.domain.dto.request.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.request.PrepareRequest;
import com.erumpay.payment.core.domain.dto.request.RemoteMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.response.CanceledResponse;
import com.erumpay.payment.core.domain.dto.response.CardPaymentHistoryResponse;
import com.erumpay.payment.core.domain.dto.response.PaymentDetailResponse;
import com.erumpay.payment.core.domain.dto.response.PaymentListResonse;
import com.erumpay.payment.core.domain.dto.response.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.response.PrepareResponse;
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

    // [be] 다윤 260605 18:00 | 대리자 결제 요청
    @PostMapping("/prepare-proxy")
    @Operation(summary = "대리자 결제 생성 요청", description = "원격결제 대리자의 결제 생성을 요청한다.")
    public ResponseEntity<PrepareResponse> prepareDeputy(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RemoteMemberPrepareRequest request) {

        log.info("/payment/prepare-proxy Controller");

        return coreService.prepareProxy(userId, idempotencyKey, request);
    }

    // [be] 다윤 260526 결제 SSE 연결
    @GetMapping("/{paymentId}/subscribe")
    @Operation(summary = "결제 상태 SSE 구독", description = "결제 상태 변경 이벤트를 SSE로 구독한다. 이벤트명은 connected/payment-updated를 사용한다.")
    public SseEmitter sseStream(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "결제 ID", required = true) @PathVariable("paymentId") Long paymentId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        if (!coreService.userCanAccess(paymentId, userId)) {
            throw new CustomException(ErrorCode.PAYMENT_OWNER_MISMATCH);
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

    @PostMapping("/request-direct")
    @Operation(summary = "비밀번호 확인 후 직접 선택 카드 결제 요청", description = "결제 PIN을 검증하고 사용자가 직접 선택한 카드 1장으로 실제 결제를 요청한다.")
    public ResponseEntity<PinAndPayResponse> requestDirectPay(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "멱등성 키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DirectPinAndPayRequest request) {

        log.info("/payment/request-direct controller");

        return coreService.requestDirectPay(userId, idempotencyKey, request);
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
    @Operation(summary = "결제 내역 전체 조회", description = "결제 내역을 날짜/결제수단/적용유형 조건으로 조회한다.")
    public ResponseEntity<PaymentListResonse> getAllPayments(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "조회할 페이지 번호(start = 0)", required = false, example = "0") @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "조회할 결제 상태(ALL, PAID, CANCELED)", required = false, example = "ALL") @RequestParam(name = "status", defaultValue = "ALL") String status,
            @Parameter(description = "빠른 기간 필터(WEEK, MONTH, YEAR). 선택한 경우에만 전달", required = false) @RequestParam(name = "period", required = false) String period,
            @Parameter(description = "조회 시작일(yyyy-MM-dd). 선택한 경우에만 전달", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(name = "start", required = false) LocalDate start,
            @Parameter(description = "조회 종료일(yyyy-MM-dd). 선택한 경우에만 전달", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @RequestParam(name = "end", required = false) LocalDate end,
            @Parameter(description = "결제수단(SINGLE, DUTCH, REMOTE). 선택한 경우에만 전달", required = false) @RequestParam(name = "paymentType", required = false) String paymentType,
            @Parameter(description = "적용유형(BENEFIT_SINGLE, BENEFIT_SPLIT, PERF_SINGLE, PERF_SPLIT). 선택한 경우에만 전달", required = false) @RequestParam(name = "strategyType", required = false) String strategyType) {
        log.info("/payment controller");
        return ResponseEntity.ok(coreService.getAllPayments(
                userId,
                page,
                status,
                period,
                start,
                end,
                paymentType,
                strategyType));
    }

    @GetMapping("/{paymentId}")
    @Operation(summary = "특정 결제 내역 조회", description = "결제된 내역 한 개를 조회한다.")
    public ResponseEntity<PaymentDetailResponse> getDetailPayment(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "결제 ID", required = true) @PathVariable("paymentId") Long paymentId) {

        log.info("/paymentId controller");
        return ResponseEntity.ok(coreService.getDetailPayment(userId, paymentId));
    }

    // [be] 다윤 260610 10:00 | 카드 1개에 대한 전체 결제 내역 조회
    @GetMapping("/cards/{cardId}")
    @Operation(summary = "카드별 결제 내역 조회", description = "카드 1개에 대한 전체 결제 내역을 조회한다.")
    public ResponseEntity<CardPaymentHistoryResponse> getCardPayment(
            @Parameter(description = "요청 사용자 ID", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "카드 ID", required = true) @PathVariable("cardId") Long cardId) {
        log.info("/payment/cards/{} controller", cardId);
        return ResponseEntity.ok(coreService.getCardPayment(userId, cardId));
    }

}
