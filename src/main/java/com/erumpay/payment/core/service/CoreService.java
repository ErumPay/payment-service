package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
import com.erumpay.payment.core.domain.dto.DutchMemberRequest;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.kafka.recommend.producer.RecommendCommandPublisher;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.service.DutchPayService;
import com.erumpay.payment.qr.service.QrService;

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
    private final QrService qrService;

    public ResponseEntity<CoreResponse> prepare(Long userId, CoreRequest request) {
        log.info("/payment/prepare Service");

        // [be] 다윤 260522 유효성 검증
        Optional<ResponseEntity<CoreResponse>> idempotentResponse = validateIdempotency(userId,
                request.getIdempotencyKey());
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        if (!payment.getAmount().equals(request.getAmount())) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
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
                CoreEntity.DutchRole.HOST,
                now);

        // [be] 다윤 260521 DB unique 처리
        try {
            coreRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        // [be] 다윤 260526 대표자 세션아이디 요청
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

        return ResponseEntity.ok(CoreResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    public ResponseEntity<CoreResponse> prepareMember(Long userId, DutchMemberRequest request) {

        Optional<ResponseEntity<CoreResponse>> idempotentResponse = validateIdempotency(userId,
                request.getIdempotencyKey());
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        LocalDateTime now = LocalDateTime.now();
        CoreEntity payment;
        try {
            payment = coreRepository.saveAndFlush(CoreEntity.builder()
                    .userId(userId)
                    .merchant_id(request.getMerchantId())
                    .idempotencyKey(request.getIdempotencyKey())
                    .order_no(qrService.generateUniqueOrderNo(now))
                    .order_name(request.getOrderName())
                    .amount(request.getAmount())
                    .payment_status(CoreEntity.PaymentStatus.PAY_PENDING)
                    .payment_type(CoreEntity.PaymentType.DUTCH)
                    .channel_type(CoreEntity.ChannelType.OFFLINE)
                    .dutch_role(CoreEntity.DutchRole.MEMBER)
                    .dutch_session_id(request.getSessionId())
                    .updated_at(now)
                    .created_at(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            Optional<ResponseEntity<CoreResponse>> replayed = validateIdempotency(userId, request.getIdempotencyKey());
            if (replayed.isPresent()) {
                return replayed.get();
            }
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recommendCommandPublisher.publishRecommendationRequested(payment);
            }
        });

        return ResponseEntity.ok(CoreResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    private Optional<ResponseEntity<CoreResponse>> validateIdempotency(Long userId, String idempotencyKey) {
        Optional<CoreEntity> existing = coreRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        CoreEntity.PaymentStatus status = existing.get().getPayment_status();
        if (status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED) {
            return Optional.of(ResponseEntity.ok(CoreResponse.builder()
                    .paymentId(existing.get().getPaymentId())
                    .paymentStatus(status.name())
                    .build()));
        }

        throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
    }

    private CoreEntity.PaymentType parsePaymentType(String paymentType) {
        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

    }

    // [be] 다윤 260522 SSE 연결 가능 여부 판단
    @Transactional(readOnly = true)
    public boolean userCanAccess(Long paymentId, Long userId) {
        return coreRepository.existsByPaymentIdAndUserId(paymentId, userId);
    }

}
