package com.erumpay.payment.dutch.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.dutch.domain.dto.DutchPayAmountRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteLinkResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantsConfirmRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPaySplitMethodRequest;
import com.erumpay.payment.dutch.service.DutchPayService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class DutchPayController {

    private final DutchPayService dutchPayService;

    @PostMapping("/internal/v1/dutch-pay/sessions")
    public ResponseEntity<DutchPayCreateResponse> createSession(@RequestBody DutchPayCreateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions Controller");

        return ResponseEntity.ok(dutchPayService.createSession(request));
    }

    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/host-authorization-result")
    public ResponseEntity<DutchPayCreateResponse> applyHostAuthorizationResult(
            @PathVariable Long session_id,
            @RequestBody DutchPayHostAuthorizationResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/host-authorization-result Controller", session_id);

        return ResponseEntity.ok(dutchPayService.applyHostAuthorizationResult(session_id, request));
    }

    @PostMapping("/internal/v1/dutch-pay/sessions/{session_id}/participants/validate-payment")
    public ResponseEntity<DutchPayParticipantPaymentValidateResponse> validateParticipantPayment(
            @PathVariable Long session_id,
            @RequestBody DutchPayParticipantPaymentValidateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/participants/validate-payment Controller", session_id);

        return ResponseEntity.ok(dutchPayService.validateParticipantPayment(session_id, request));
    }

    @GetMapping("/api/v1/dutch-pay/sessions/{session_id}")
    public ResponseEntity<DutchPaySessionDetailResponse> getSession(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{} Controller", session_id);

        return ResponseEntity.ok(dutchPayService.getSession(userId, session_id));
    }

    @GetMapping("/api/v1/dutch-pay/sessions/active")
    public ResponseEntity<List<DutchPaySessionDetailResponse>> getActiveSessions(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("/api/v1/dutch-pay/sessions/active Controller");

        return ResponseEntity.ok(dutchPayService.getActiveSessions(userId));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/invites")
    public ResponseEntity<DutchPaySessionDetailResponse> inviteAppFriends(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id,
            @Valid @RequestBody DutchPayInviteRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/invites Controller", session_id);

        return ResponseEntity.ok(dutchPayService.inviteAppFriends(userId, session_id, request));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/invite-links")
    public ResponseEntity<DutchPayInviteLinkResponse> createInviteLink(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/invite-links Controller", session_id);

        return ResponseEntity.ok(dutchPayService.createInviteLink(userId, session_id));
    }

    @PostMapping("/api/v1/dutch-pay/invite-links/{invite_token}/accept")
    public ResponseEntity<DutchPaySessionDetailResponse> acceptInviteLink(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String invite_token) {
        log.info("/api/v1/dutch-pay/invite-links/{}/accept Controller", invite_token);

        return ResponseEntity.ok(dutchPayService.acceptInviteLink(userId, invite_token));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/reject")
    public ResponseEntity<DutchPaySessionDetailResponse> rejectInvite(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id) {
        log.info("/api/v1/dutch-pay/sessions/{}/reject Controller", session_id);

        return ResponseEntity.ok(dutchPayService.rejectInvite(userId, session_id));
    }

    @PostMapping("/api/v1/dutch-pay/sessions/{session_id}/participants/confirm")
    public ResponseEntity<DutchPaySessionDetailResponse> confirmParticipants(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id,
            @RequestBody(required = false) DutchPayParticipantsConfirmRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/participants/confirm Controller", session_id);

        return ResponseEntity.ok(dutchPayService.confirmParticipants(userId, session_id, request));
    }

    @PatchMapping("/api/v1/dutch-pay/sessions/{session_id}/split-method")
    public ResponseEntity<DutchPaySessionDetailResponse> updateSplitMethod(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id,
            @Valid @RequestBody DutchPaySplitMethodRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/split-method Controller", session_id);

        return ResponseEntity.ok(dutchPayService.updateSplitMethod(userId, session_id, request));
    }

    @PatchMapping("/api/v1/dutch-pay/sessions/{session_id}/my-amount")
    public ResponseEntity<DutchPaySessionDetailResponse> updateMyAmount(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long session_id,
            @Valid @RequestBody DutchPayAmountRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/my-amount Controller", session_id);

        return ResponseEntity.ok(dutchPayService.updateMyAmount(userId, session_id, request));
    }
}
