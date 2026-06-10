package com.erumpay.payment.dutch.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.dutch.domain.dto.DutchPayAmountRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostFinalPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteLinkResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteNotificationResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayMyPaymentResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantsConfirmRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPaySplitMethodRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayTimeoutBatchResponse;
import com.erumpay.payment.dutch.service.DutchPayService;
import com.erumpay.payment.dutch.service.DutchPaySseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class DutchPayController {

    private final DutchPayService dutchPayService;
    private final DutchPaySseService dutchPaySseService;

    // [be] 영은 260523 1120 | core가 DUTCH/HOST prepare 처리 중 호출하는 내부 세션 생성 API
    @PostMapping("/internal/v1/dutch-pay/sessions")
    public ResponseEntity<DutchPayCreateResponse> createSession(@RequestBody DutchPayCreateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions Controller");

        return ResponseEntity.ok(dutchPayService.createSession(request));
    }

    // [be] 영은 260523 1120 | core가 대표자 가승인 결과를 dutch에 전달하는 내부 콜백 API
    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/host-authorization-result")
    public ResponseEntity<DutchPayCreateResponse> applyHostAuthorizationResult(
            @PathVariable("session_id") Long session_id,
            @RequestBody DutchPayHostAuthorizationResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/host-authorization-result Controller", session_id);

        return ResponseEntity.ok(dutchPayService.applyHostAuthorizationResult(session_id, request));
    }

    // [be] 영은 260523 1120 | core가 DUTCH/MEMBER 결제 생성 전 참여자와 부담 금액을 검증하는 내부 API
    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/participants/validate-payment")
    public ResponseEntity<DutchPayParticipantPaymentValidateResponse> validateParticipantPayment(
            @PathVariable("session_id") Long session_id,
            @RequestBody DutchPayParticipantPaymentValidateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/participants/validate-payment Controller", session_id);

        return ResponseEntity.ok(dutchPayService.validateParticipantPayment(session_id, request));
    }

    // [be] 영은 260526 1620 | core/PG 결제 완료 이벤트를 받아 더치페이 참여자 결제 상태를 반영하는 내부 API
    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/participants/payment-result")
    public ResponseEntity<DutchPaySessionDetailResponse> applyParticipantPaymentResult(
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPayParticipantPaymentResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/participants/payment-result Controller", session_id);

        return ResponseEntity.ok(dutchPayService.applyParticipantPaymentResult(session_id, request));
    }

    // [be] 영은 260601 | Core가 대표자 최종 결제 완료를 전달하면 Dutch 세션을 COMPLETED로 전환하는 내부 API
    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/host-final-payment-result")
    public ResponseEntity<DutchPaySessionDetailResponse> applyHostFinalPaymentResult(
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPayHostFinalPaymentResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/host-final-payment-result Controller", session_id);

        return ResponseEntity.ok(dutchPayService.applyHostFinalPaymentResult(session_id, request));
    }

    @PostMapping("/internal/v1/dutch-pay/timeout-batch")
    public ResponseEntity<DutchPayTimeoutBatchResponse> handleTimeoutBatch() {
        log.info("/internal/v1/dutch-pay/timeout-batch Controller");

        return ResponseEntity.ok(dutchPayService.handleTimeoutBatch());
    }

    // [be] 영은 260523 1120 | 초대/알림/화면 복원 시 최신 더치페이 세션 상세를 조회하는 API
    @GetMapping("/api/v1/dutch-pay/sessions/{session_id}")
    public ResponseEntity<DutchPaySessionDetailResponse> getSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{} Controller", session_id);

        return ResponseEntity.ok(dutchPayService.getSession(userId, session_id));
    }

    // [be] 영은 260523 1120 | 알림 클릭 후 참여자가 결제 진입 화면에 표시할 본인 결제 정보를 조회하는 API
    @GetMapping("/api/v1/dutch-pay/sessions/{session_id}/my-payment")
    public ResponseEntity<DutchPayMyPaymentResponse> getMyPayment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/my-payment Controller", session_id);

        return ResponseEntity.ok(dutchPayService.getMyPayment(userId, session_id));
    }

    // [be] 영은 260523 1120 | 참여자 금액/상태 변경을 실시간으로 받는 단일 인스턴스 SSE API
    @GetMapping("/api/v1/dutch-pay/sessions/{session_id}/stream")
    public SseEmitter streamSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/stream Controller", session_id);

        return dutchPaySseService.subscribe(session_id, userId);
    }

    // [be] 영은 260523 1120 | 홈 화면 이어하기에 표시할 진행 중 더치페이 세션 목록 조회 API
    @GetMapping("/api/v1/dutch-pay/sessions/active")
    public ResponseEntity<List<DutchPaySessionDetailResponse>> getActiveSessions(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("/api/v1/dutch-pay/sessions/active Controller");

        return ResponseEntity.ok(dutchPayService.getActiveSessions(userId));
    }

    // [be] 영은 260523 1120 | 대표자가 앱 안 친구를 더치페이 그룹에 직접 초대하는 API
    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/invites")
    public ResponseEntity<DutchPaySessionDetailResponse> inviteAppFriends(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPayInviteRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/invites Controller", session_id);

        return ResponseEntity.ok(dutchPayService.inviteAppFriends(userId, session_id, request));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/invite-notifications")
    public ResponseEntity<DutchPayInviteNotificationResponse> sendInviteNotifications(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPayInviteRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/invite-notifications Controller", session_id);

        // [be] 영은 260610 | 친구에게 알림만 보내고 실제 참여자는 링크 수락 시점에 생성한다.
        return ResponseEntity.ok(dutchPayService.sendInviteNotifications(userId, session_id, request));
    }

    // [be] 영은 260523 1120 | 앱 밖 공유를 위한 서명된 초대 링크를 생성하는 API
    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/invite-links")
    public ResponseEntity<DutchPayInviteLinkResponse> createInviteLink(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/invite-links Controller", session_id);

        return ResponseEntity.ok(dutchPayService.createInviteLink(userId, session_id));
    }

    // [be] 영은 260523 1120 | 초대 링크를 통해 더치페이 그룹에 참여하는 API
    @PostMapping("/api/v1/dutch-pay/invite-links/{invite_token}/accept")
    public ResponseEntity<DutchPaySessionDetailResponse> acceptInviteLink(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("invite_token") String invite_token) {
        log.info("/api/v1/dutch-pay/invite-links/accept Controller");

        return ResponseEntity.ok(dutchPayService.acceptInviteLink(userId, invite_token));
    }

    // [be] 영은 260523 1120 | 참여자가 초대를 거절하고 CUSTOM이면 대표자 부담금을 다시 계산하는 API
    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/reject")
    public ResponseEntity<DutchPaySessionDetailResponse> rejectInvite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/reject Controller", session_id);

        return ResponseEntity.ok(dutchPayService.rejectInvite(userId, session_id));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/cancel")
    public ResponseEntity<DutchPaySessionDetailResponse> cancelSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/cancel Controller", session_id);

        // [be] 영은 260610 | 결제 시작 전 대표자가 더치페이 그룹을 서버 상태 기준으로 취소한다.
        return ResponseEntity.ok(dutchPayService.cancelSession(userId, session_id));
    }

    @DeleteMapping("/api/v1/dutch-pay/sessions/{session_id}/participants/{participant_user_id}")
    public ResponseEntity<DutchPaySessionDetailResponse> removeParticipant(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @PathVariable("participant_user_id") Long participant_user_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/participants/{} Controller", session_id, participant_user_id);

        // [be] 영은 260610 | 대표자가 결제 시작 전 참여자를 내보내고 남은 참여자 기준으로 금액을 다시 계산한다.
        return ResponseEntity.ok(dutchPayService.removeParticipant(userId, session_id, participant_user_id));
    }

    // [be] 영은 260523 1120 | 대표자가 참여 인원을 확정하고 금액 배분 단계로 전환하는 API
    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/participants/confirm")
    public ResponseEntity<DutchPaySessionDetailResponse> confirmParticipants(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @RequestBody(required = false) DutchPayParticipantsConfirmRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/participants/confirm Controller", session_id);

        return ResponseEntity.ok(dutchPayService.confirmParticipants(userId, session_id, request));
    }

    // [be] 영은 260523 1120 | 대표자가 EQUAL/CUSTOM 배분 방식을 변경하는 API
    @PatchMapping("/api/v1/dutch-pay/sessions/{session_id}/split-method")
    public ResponseEntity<DutchPaySessionDetailResponse> updateSplitMethod(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPaySplitMethodRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/split-method Controller", session_id);

        return ResponseEntity.ok(dutchPayService.updateSplitMethod(userId, session_id, request));
    }

    // [be] 영은 260523 1120 | CUSTOM 배분에서 참여자가 본인 부담 금액을 입력하거나 수정하는 API
    @PatchMapping("/api/v1/dutch-pay/sessions/{session_id}/my-amount")
    public ResponseEntity<DutchPaySessionDetailResponse> updateMyAmount(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("session_id") Long session_id,
            @Valid @RequestBody DutchPayAmountRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/my-amount Controller", session_id);

        return ResponseEntity.ok(dutchPayService.updateMyAmount(userId, session_id, request));
    }
}
