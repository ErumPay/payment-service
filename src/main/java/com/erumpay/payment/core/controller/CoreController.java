package com.erumpay.payment.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
import com.erumpay.payment.core.domain.dto.DutchMemberRequest;
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
    public ResponseEntity<CoreResponse> prepare(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CoreRequest request) {

        log.info("/payment/prepare Controller");

        return coreService.prepare(userId, request);
    }

    // [be] 다윤 260522 참여자 결제 요청
    @PostMapping("/prepare-member")
    public ResponseEntity<CoreResponse> prepareMember(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody DutchMemberRequest request) {

        log.info("/payment/prepare-member Controller");

        return coreService.prepareMember(userId, request);
    }

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

}