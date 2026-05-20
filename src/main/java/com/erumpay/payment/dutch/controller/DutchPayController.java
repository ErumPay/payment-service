package com.erumpay.payment.dutch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.service.DutchPayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
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
}
