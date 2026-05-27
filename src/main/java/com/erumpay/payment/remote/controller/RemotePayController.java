package com.erumpay.payment.remote.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.service.RemotePayService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Validated
public class RemotePayController {

    private final RemotePayService remotePayService;

    @PostMapping("/api/v1/remote-pay/requests")
    public ResponseEntity<RemotePayCreateResponse> createRequest(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Controller");

        return ResponseEntity.ok(remotePayService.createRequest(userId, request));
    }
}
