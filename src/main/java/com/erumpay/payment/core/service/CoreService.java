package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CoreService {
    private final CoreRepository coreRepository;

    public ResponseEntity<CoreResponse> prepare(CoreRequest request) {
        log.info("/payment/prepare Service");

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (coreRepository.existsByIdempotencyKey(request.getIdempotencyKey())) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        CoreEntity.PaymentType paymentType = parsePaymentType(request.getPaymentType());
        LocalDateTime now = LocalDateTime.now();

        payment.preparePayment(
                request.getIdempotencyKey(),
                request.getUserId(),
                paymentType,
                now);

        coreRepository.save(payment);

        return ResponseEntity.ok(CoreResponse.builder()
                .paymentId(payment.getPayment_id())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    private CoreEntity.PaymentType parsePaymentType(String paymentType) {
        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

    }

}
