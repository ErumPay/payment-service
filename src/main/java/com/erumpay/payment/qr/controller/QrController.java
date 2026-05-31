package com.erumpay.payment.qr.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.qr.domain.dto.QrRequest;
import com.erumpay.payment.qr.domain.dto.QrResponse;
import com.erumpay.payment.qr.domain.dto.QrValidateRequest;
import com.erumpay.payment.qr.service.QrService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/api/v1/payment/qr")
@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Core Payment", description = "QR 생성 API")
public class QrController {

    private final QrService qrService;

    @PostMapping(value = "/request", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> createQR(
            @Valid @RequestBody QrRequest request) throws Exception {
        log.info("/qr/request Controller");

        return qrService.createQR(request);
    }

    @PostMapping("/validate")
    public ResponseEntity<QrResponse> validateQR(@RequestBody QrValidateRequest request) {
        log.info("/qr/validate Controller");

        return qrService.validateQR(request);
    }
}
