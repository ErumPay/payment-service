package com.erumpay.payment.dutch.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationRequest;
import com.erumpay.payment.dutch.service.DutchPayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/api/v1/dutch-pay")
@RestController
@Slf4j
@RequiredArgsConstructor
public class DutchPayController {

    private final DutchPayService dutchPayService;

    @PostMapping("/sessions")
    public ResponseEntity<DutchPayCreateResponse> createSession(@RequestBody DutchPayCreateRequest request) {
        log.info("/api/v1/dutch-pay/sessions Controller");

        return ResponseEntity.ok(dutchPayService.createSession(request));
    }

    @PostMapping("/sessions/{session_id}/host-authorizations")
    public ResponseEntity<DutchPayCreateResponse> authorizeHostPayment(
            @PathVariable Long session_id,
            @RequestBody DutchPayHostAuthorizationRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/host-authorizations Controller", session_id);

        return ResponseEntity.ok(dutchPayService.authorizeHostPayment(session_id, request));
    }
}
