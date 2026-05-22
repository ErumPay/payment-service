package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.kafka.recommend.producer.RecommendCommandPublisher;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.service.DutchPayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CoreService {
    private final CoreRepository coreRepository;
    private final RecommendCommandPublisher recommendCommandPublisher;
    private final DutchPayService dutchPayService;

    public ResponseEntity<CoreResponse> prepare(Long userId, CoreRequest request) {
        log.info("/payment/prepare Service");

        // [be] 다윤 260522 유효성 검증
        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (payment.getPayment_status() != CoreEntity.PaymentStatus.CREATED) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        CoreEntity.PaymentType paymentType = parsePaymentType(request.getPaymentType());
        LocalDateTime now = LocalDateTime.now();

        payment.preparePayment(
                request.getIdempotencyKey(),
                userId,
                paymentType,
                now);

        // [be] 다윤 260521 DB unique 처리
        try {
            coreRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        if (paymentType == CoreEntity.PaymentType.DUTCH) {
            if (!payment.getAmount().equals(request.getAmount())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            DutchPayCreateResponse dutchResponse = dutchPayService.createSession(
                    DutchPayCreateRequest.builder()
                            .host_payment_id(request.getPaymentId())
                            .host_user_id(userId)
                            .merchant_id(payment.getMerchant_id())
                            .total_amount(payment.getAmount())
                            .order_name(payment.getOrder_name())
                            .build());

            // log.info("dutch session response: {}", dutchResponse);

            payment.dutchSessionPayment(dutchResponse.getSession_id(), CoreEntity.DutchRole.HOST);
        }

        // [be] 다윤 260521 outbox pattern 변경 가능
        if (paymentType == CoreEntity.PaymentType.SINGLE || paymentType == CoreEntity.PaymentType.DUTCH) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recommendCommandPublisher.publishRecommendationRequested(payment);
                }
            });
        }

        return ResponseEntity.ok(CoreResponse.builder()
                .paymentId(payment.getPaymentId())
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

    @Transactional(readOnly = true)
    public boolean userCanAccess(Long paymentId, Long userId) {
        return coreRepository.existsByPaymentIdAndUserId(paymentId, userId);
    }

}
