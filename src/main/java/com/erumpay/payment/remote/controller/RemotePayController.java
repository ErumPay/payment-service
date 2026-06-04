package com.erumpay.payment.remote.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.remote.domain.dto.RemotePayCoreCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayDraftCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayExpireBatchResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayPaymentConnectRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayRejectRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayTargetAssignRequest;
import com.erumpay.payment.remote.service.RemotePayService;
import com.erumpay.payment.remote.service.RemotePaySseService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class RemotePayController {

    private final RemotePayService remotePayService;
    private final RemotePaySseService remotePaySseService;

    // [be] 영은 260527 1010 | 원격결제 상세 조회 - 알림 클릭/화면 복원 시 요청자와 대상자만 현재 요청 상태를 확인한다.
    @GetMapping("/api/v1/remote-pay/requests/{request_id}")
    public ResponseEntity<RemotePayCreateResponse> getRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{} Controller", request_id);

        return ResponseEntity.ok(remotePayService.getRequest(userId, request_id));
    }

    // [be] 영은 260527 1020 | 원격결제 진행 목록 조회 - 사용자가 요청자 또는 대상자인 DRAFT/PENDING 요청을 홈/알림함에 노출한다.
    @GetMapping("/api/v1/remote-pay/requests/active")
    public ResponseEntity<List<RemotePayCreateResponse>> getActiveRequests(
            @RequestHeader("X-User-Id") @Positive Long userId) {
        log.info("/api/v1/remote-pay/requests/active Controller");

        return ResponseEntity.ok(remotePayService.getActiveRequests(userId));
    }

    // [be] 영은 260528 1110 | 원격결제 상태 SSE - 요청자/대상자가 같은 request_id의 상태 변경을 실시간으로 받는다.
    @GetMapping("/api/v1/remote-pay/requests/{request_id}/stream")
    public SseEmitter streamRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{}/stream Controller", request_id);

        return remotePaySseService.subscribe(request_id, userId);
    }

    // [be] 영은 260527 1000 | 원격결제 요청 생성 - targetUserId를 이미 알고 있는 초기 테스트/직접 생성용 공개 API다.
    // [be] 영은 260605 1530 | 현재 모바일 기본 흐름은 Core가 draft를 만들고, 이후 target 지정 API로 대리결제자를 연결한다.
    @PostMapping("/api/v1/remote-pay/requests")
    public ResponseEntity<RemotePayCreateResponse> createRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Controller");

        return ResponseEntity.ok(remotePayService.createRequest(userId, request));
    }

    // [be] 영은 260528 1010 | targetUserId를 이미 알고 있을 때 Core가 호출하는 기존 내부 생성 API다.
    // [be] 영은 260605 1530 | target을 나중에 고르는 화면에서는 /draft를 먼저 호출한다.
    @PostMapping("/internal/v1/remote-pay/requests")
    public ResponseEntity<RemotePayCreateResponse> createRequestFromCore(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody RemotePayCoreCreateRequest request) {
        log.info("/internal/v1/remote-pay/requests Controller");

        return ResponseEntity.ok(remotePayService.createRequestFromCore(userId, request));
    }

    // [be] 영은 260605 1530 | Core /payment/prepare REMOTE 시점에 호출한다.
    // [be] 영은 260605 1530 | 아직 대리결제자 선택 전이므로 DRAFT 상태 request_id만 먼저 발급한다.
    @PostMapping("/internal/v1/remote-pay/requests/draft")
    public ResponseEntity<RemotePayCreateResponse> createDraftFromCore(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody RemotePayDraftCreateRequest request) {
        log.info("/internal/v1/remote-pay/requests/draft Controller");

        return ResponseEntity.ok(remotePayService.createDraftFromCore(userId, request));
    }

    // [be] 영은 260605 1530 | 요청자가 친구 선택을 완료하면 DRAFT 요청에 target_user_id를 연결하고 PENDING으로 전환한다.
    @PostMapping("/api/v1/remote-pay/requests/{request_id}/target")
    public ResponseEntity<RemotePayCreateResponse> assignTarget(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id,
            @Valid @RequestBody RemotePayTargetAssignRequest request) {
        log.info("/api/v1/remote-pay/requests/{}/target Controller", request_id);

        return ResponseEntity.ok(remotePayService.assignTarget(userId, request_id, request));
    }

    // [be] 영은 260605 1530 | 대리결제자 Core /payment/prepare 이후 생성된 payer payment_id를 원격결제 요청에 연결한다.
    @PostMapping("/internal/v1/remote-pay/requests/{request_id}/payment")
    public ResponseEntity<RemotePayCreateResponse> connectPaymentFromCore(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id,
            @Valid @RequestBody RemotePayPaymentConnectRequest request) {
        log.info("/internal/v1/remote-pay/requests/{}/payment Controller", request_id);

        return ResponseEntity.ok(remotePayService.connectPaymentForPrepare(
                userId,
                request_id,
                request.getPayer_payment_id()));
    }

    // [be] 영은 260528 1030 | 원격결제 거절 - 요청받은 사람이 PENDING 상태의 원격결제 요청을 거절한다.
    @PostMapping("/api/v1/remote-pay/requests/{request_id}/reject")
    public ResponseEntity<RemotePayCreateResponse> rejectRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id,
            @Valid @RequestBody(required = false) RemotePayRejectRequest request) {
        log.info("/api/v1/remote-pay/requests/{}/reject Controller", request_id);

        String rejectReason = request == null ? null : request.getReject_reason();
        return ResponseEntity.ok(remotePayService.rejectRequest(userId, request_id, rejectReason));
    }

    // [be] 영은 260527 1050 | 원격결제 취소 - 요청자가 결제 준비 전 요청을 취소한다.
    @PostMapping("/api/v1/remote-pay/requests/{request_id}/cancel")
    public ResponseEntity<RemotePayCreateResponse> cancelRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable("request_id") @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{}/cancel Controller", request_id);

        return ResponseEntity.ok(remotePayService.cancelRequest(userId, request_id));
    }

    // [be] 영은 260528 1120 | 원격결제 만료 처리 배치 - 외부 공개 없이 스케줄러/운영 호출에서만 사용한다.
    @PostMapping("/internal/v1/remote-pay/expire-batch")
    public ResponseEntity<RemotePayExpireBatchResponse> expireBatch() {
        log.info("/internal/v1/remote-pay/expire-batch Controller");

        return ResponseEntity.ok(remotePayService.expirePendingRequests());
    }
}
