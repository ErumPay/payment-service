package com.erumpay.payment.core.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
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

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
@Slf4j
public class CoreController {

    private final CoreService orderService;
    private final CoreSseService coreSseService;

    @PostMapping("/prepare")
    public ResponseEntity<CoreResponse> prepare(@Valid @RequestBody CoreRequest request) {

        log.info("/payment/prepare Controller");

        return orderService.prepare(request);
    }

    @GetMapping("/{paymentId}/subscribe")
    public SseEmitter sseStream(@PathVariable Long paymentId) {
        log.info("/payment/{}/subscribe SSE subscribe", paymentId);
        return coreSseService.subscribe(paymentId);
    }

}
