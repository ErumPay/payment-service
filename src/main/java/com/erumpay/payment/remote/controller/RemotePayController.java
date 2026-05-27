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

import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayRejectRequest;
import com.erumpay.payment.remote.service.RemotePayService;

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

    // [be] 영은 260527 1010 | 원격결제 상세 조회 - 알림 클릭/화면 복원 시 요청자와 대상자만 현재 요청 상태를 확인한다.
    @GetMapping("/api/v1/remote-pay/requests/{request_id}")
    public ResponseEntity<RemotePayCreateResponse> getRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{} Controller", request_id);

        return ResponseEntity.ok(remotePayService.getRequest(userId, request_id));
    }

    // [be] 영은 260527 1020 | 원격결제 진행 목록 조회 - 사용자가 요청자 또는 대상자인 PENDING 요청을 홈/알림함에 노출한다.
    @GetMapping("/api/v1/remote-pay/requests/active")
    public ResponseEntity<List<RemotePayCreateResponse>> getActiveRequests(
            @RequestHeader("X-User-Id") @Positive Long userId) {
        log.info("/api/v1/remote-pay/requests/active Controller");

        return ResponseEntity.ok(remotePayService.getActiveRequests(userId));
    }

    // [be] 영은 260527 1000 | 원격결제 요청 생성 - 결제 실행은 만들지 않고 target에게 결제 요청만 생성한다.
    // [be] 영은 260527 1000 | 실제 결제 준비는 기존 Core /api/v1/payment/prepare 흐름에서 처리한다.
    @PostMapping("/api/v1/remote-pay/requests")
    public ResponseEntity<RemotePayCreateResponse> createRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @Valid @RequestBody RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Controller");

        return ResponseEntity.ok(remotePayService.createRequest(userId, request));
    }

    // [be] 영은 260527 1040 | 원격결제 거절 - 요청받은 사람이 결제 준비 전 요청을 거절한다.
    @PostMapping("/api/v1/remote-pay/requests/{request_id}/reject")
    public ResponseEntity<RemotePayCreateResponse> rejectRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long request_id,
            @Valid @RequestBody(required = false) RemotePayRejectRequest request) {
        log.info("/api/v1/remote-pay/requests/{}/reject Controller", request_id);

        String rejectReason = request == null ? null : request.getReject_reason();
        return ResponseEntity.ok(remotePayService.rejectRequest(userId, request_id, rejectReason));
    }

    // [be] 영은 260527 1050 | 원격결제 취소 - 요청자가 결제 준비 전 요청을 취소한다.
    @PostMapping("/api/v1/remote-pay/requests/{request_id}/cancel")
    public ResponseEntity<RemotePayCreateResponse> cancelRequest(
            @RequestHeader("X-User-Id") @Positive Long userId,
            @PathVariable @Positive Long request_id) {
        log.info("/api/v1/remote-pay/requests/{}/cancel Controller", request_id);

        return ResponseEntity.ok(remotePayService.cancelRequest(userId, request_id));
    }
}
