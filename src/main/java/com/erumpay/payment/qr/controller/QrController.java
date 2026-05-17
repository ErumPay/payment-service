package com.erumpay.payment.qr.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.qr.domain.dto.QrRequest;
import com.erumpay.payment.qr.service.QrService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("/qr")
@RestController
@Slf4j
@RequiredArgsConstructor
public class QrController {

    private final QrService qrService;

    @PostMapping(value = "/request", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> createQR(
            @RequestBody QrRequest request) throws Exception {
        log.info("/qr/request Controller");

        return qrService.createQR(request);
    }

}
