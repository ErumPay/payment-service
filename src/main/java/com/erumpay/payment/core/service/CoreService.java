package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.core.client.auth.AuthClient;
import com.erumpay.payment.core.client.auth.dto.AuthPinRequest;
import com.erumpay.payment.core.client.auth.dto.AuthPinResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.dao.CardDetailRepository;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.PrepareRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.DutchMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.CanceledResponse;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
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
    private static final String AUTHORIZATION = "Bearer server-test-token";
    private static final String PG_STATUS_REJECTED = "REJECTED";

    private final PgClient pgClient;
    private final CardDetailRepository cardDetailRepository;
    private final CoreRepository coreRepository;
    private final CoreValidationService coreValidationService;
    private final CorePgPaymentService corePgPaymentService;
    private final RecommendCommandPublisher recommendCommandPublisher;
    private final AuthClient authClient;
    private final DutchPayService dutchPayService;
    private final QrService qrService;

    // [be] 다윤 260526 결제 요청 시작 - 개인, 더치페이 대표자
    public ResponseEntity<PrepareResponse> prepare(Long userId, String idempotencyKey, PrepareRequest request) {
        log.info("/payment/prepare Service");

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = coreValidationService.validateIdempotency(userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }

        coreValidationService.validatePrepareStatus(payment.getPayment_status());

        CoreEntity.PaymentType paymentType = coreValidationService.parsePaymentType(request.getPaymentType());
        LocalDateTime now = LocalDateTime.now();

        payment.preparePayment(
                normalizedIdempotencyKey,
                userId,
                paymentType,
                now);

        // [be] 다윤 260521 DB unique 처리
        try {
            coreRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        // [be] 다윤 260526 대표자 세션아이디 요청
        if (paymentType == CoreEntity.PaymentType.DUTCH) {

            DutchPayCreateResponse dutchResponse = dutchPayService.createSession(
                    DutchPayCreateRequest.builder()
                            .host_payment_id(request.getPaymentId())
                            .host_user_id(userId)
                            .merchant_id(payment.getMerchant_id())
                            .total_amount(payment.getAmount())
                            .order_name(payment.getOrder_name())
                            .build());

            // log.info("dutch session response: {}", dutchResponse);

            payment.hostDutchSessionPayment(dutchResponse.getSession_id(), CoreEntity.DutchRole.HOST);
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
        return ResponseEntity.ok(PrepareResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 참여자
    public ResponseEntity<PrepareResponse> prepareMember(
            Long userId,
            String idempotencyKey,
            DutchMemberPrepareRequest request) {

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = coreValidationService.validateIdempotency(userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        LocalDateTime now = LocalDateTime.now();
        CoreEntity payment;
        try {
            payment = coreRepository.saveAndFlush(CoreEntity.builder()
                    .userId(userId)
                    .merchant_id(request.getMerchantId())
                    .idempotencyKey(normalizedIdempotencyKey)
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
            Optional<ResponseEntity<PrepareResponse>> replayed = coreValidationService.validateIdempotency(userId,
                    normalizedIdempotencyKey);
            if (replayed.isPresent()) {
                return replayed.get();
            }
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        dutchPayService.registerParticipantPayment(
                request.getSessionId(),
                request.getParticipantId(),
                userId,
                payment);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                recommendCommandPublisher.publishRecommendationRequested(payment);
            }
        });

        return ResponseEntity.ok(PrepareResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    public ResponseEntity<PinAndPayResponse> request(Long userId, String idempotencyKey, PinAndPayRequest request) {

        log.info("/payment/request Service");

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || !savedIdempotencyKey.equals(normalizedIdempotencyKey)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        coreValidationService.validateRequestStatus(payment.getPayment_status());

        if (!payment.getAmount().equals(request.getTotalAmount())) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }

        coreValidationService.validateCardAmounts(request);

        // [be] 다윤 260526 auth-service pin 인증 요청
        // AuthResponse authResponse = verifyPin(userId, request.getPin());

        payment.pgRequestUpdateStatusPayment(LocalDateTime.now());

        // [be] 다윤 260526 pg-payment-service 실결제 요청
        corePgPaymentService.requestPgPayments(payment, request);

        return ResponseEntity.ok(PinAndPayResponse.builder()
                .paymentId(payment.getPaymentId())
                .userId(payment.getUserId())
                .paymentStatus(payment.getPayment_status().name())
                .paymentType(payment.getPayment_type().name())
                .build());
    }

    // [be] 다윤 260526 auth-service pin 인증 요청
    private AuthPinResponse verifyPin(Long userId, String pin) {
        AuthPinResponse res;
        try {
            res = authClient.verifyPaymentPassword(
                    AuthPinRequest.builder()
                            .pin(pin)
                            .userId(userId)
                            .build());

            log.info("auth feign response : {}", res);
        } catch (FeignException e) {
            log.error("auth feign error. status={}, body={}", e.status(),
                    e.contentUTF8());

            if (e.status() == 400 || e.status() == 401 || e.status() == 404 || e.status() == 423) {
                throw new CustomException(ErrorCode.PIN_INVALID);
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (res == null || !res.isVerified()) {
            throw new CustomException(ErrorCode.PIN_INVALID);
        }

        return res;
    }

    public CanceledResponse cancel(Long userId, String idempotencyKey, Long paymentId) {

        log.info("/payment/cancel Service");
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        List<CardDetailEntity> cards = cardDetailRepository.findCancelableCardsByPaymentId(paymentId);
        List<Long> canceledPgTxnIds = cards.stream()
                .map(CardDetailEntity::getPg_txn_id)
                .collect(Collectors.toList());

        if (payment.getPayment_status() == CoreEntity.PaymentStatus.VOIDED) {
            return CanceledResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .paymentStatus(payment.getPayment_status().name())
                    .canceledAt(payment.getCanceled_at())
                    // .canceledPgTxnIds(canceledPgTxnIds)
                    .build();
        }

        if (payment.getPayment_status() != CoreEntity.PaymentStatus.PAID) {
            throw new CustomException(ErrorCode.CANCELED_INVALID);
        }

        if (cards.isEmpty()) {
            throw new CustomException(ErrorCode.CANCELED_CARD_INVALID);
        }

        for (CardDetailEntity card : cards) {
            PgPayCancelRequest cancelRequest = PgPayCancelRequest.builder()
                    .payPaymentId(paymentId)
                    .merchantId(payment.getMerchant_id())
                    .cancelReason("USER_REQUEST")
                    .build();

            PgAuthPayResponse pgResponse;
            String cancelIdempotencyKey = normalizedIdempotencyKey + "-" + card.getPg_txn_id();
            try {
                pgResponse = pgClient.pgPaymentCancelRequest(
                        AUTHORIZATION,
                        cancelIdempotencyKey,
                        card.getPg_txn_id(),
                        cancelRequest);
            } catch (FeignException e) {
                log.error("pg cancel feign error. status={}, body={}", e.status(), e.contentUTF8());
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.BAD_REQUEST);
                }
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
            }

            if (pgResponse == null || pgResponse.getStatus() == null) {
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
            }

            if (PG_STATUS_REJECTED.equals(pgResponse.getStatus())) {
                throw new CustomException(ErrorCode.CANCELED_PG_REJECTED);
            }
        }

        LocalDateTime canceledAt = LocalDateTime.now();
        payment.voidedStatusUpdatePayment(canceledAt);
        return CanceledResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .canceledAt(canceledAt)
                // .canceledPgTxnIds(canceledPgTxnIds)
                .build();
    }

    // [be] 다윤 260522 SSE 연결 가능 여부 판단
    @Transactional(readOnly = true)
    public boolean userCanAccess(Long paymentId, Long userId) {
        return coreRepository.existsByPaymentIdAndUserId(paymentId, userId);
    }
}
