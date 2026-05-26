package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import feign.FeignException;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.core.client.auth.AuthClient;
import com.erumpay.payment.core.client.auth.dto.AuthRequest;
import com.erumpay.payment.core.client.auth.dto.AuthResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthResponse;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.CoreRequest;
import com.erumpay.payment.core.domain.dto.CoreResponse;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.DutchMemberRequest;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
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
    private final EventRepository eventRepository;
    private final RecommendCommandPublisher recommendCommandPublisher;
    private final AuthClient authClient;
    private final PgClient pgClient;
    private final DutchPayService dutchPayService;
    private final QrService qrService;

    // [be] 다윤 260526 결제 요청 시작 - 개인, 더치페이 대표자
    public ResponseEntity<CoreResponse> prepare(Long userId, String idempotencyKey, CoreRequest request) {
        log.info("/payment/prepare Service");

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        Optional<ResponseEntity<CoreResponse>> idempotentResponse = validateIdempotency(userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }

        validatePrepareStatus(payment.getPayment_status());

        CoreEntity.PaymentType paymentType = parsePaymentType(request.getPaymentType());
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
        return ResponseEntity.ok(CoreResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status().name())
                .build());
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 참여자
    public ResponseEntity<CoreResponse> prepareMember(
            Long userId,
            String idempotencyKey,
            DutchMemberRequest request) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        Optional<ResponseEntity<CoreResponse>> idempotentResponse = validateIdempotency(userId,
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
            Optional<ResponseEntity<CoreResponse>> replayed = validateIdempotency(userId, normalizedIdempotencyKey);
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

    // [be] 다윤 260522 SSE 연결 가능 여부 판단
    @Transactional(readOnly = true)
    public boolean userCanAccess(Long paymentId, Long userId) {
        return coreRepository.existsByPaymentIdAndUserId(paymentId, userId);
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    public ResponseEntity<AuthResponse> request(Long userId, String idempotencyKey, PinAndPayRequest request) {

        log.info("/payment/request Service");

        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = coreRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || !savedIdempotencyKey.equals(normalizedIdempotencyKey)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        validateRequestStatus(payment.getPayment_status());

        if (!payment.getAmount().equals(request.getTotalAmount())) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }

        validateCardAmounts(request);

        // [be] 다윤 260526 auth-service pin 인증 요청
        // AuthResponse authResponse = verifyPin(userId, request.getPin());

        payment.pgRequestUpdateStatusPayment(LocalDateTime.now());

        // [be] 다윤 260526 pg-payment-service 실결제 요청
        requestPgPayments(payment, request);

        return ResponseEntity.ok(AuthResponse.builder().verified(true).build());
    }

    // [be] 다윤 260526 auth-service pin 인증 요청
    private AuthResponse verifyPin(Long userId, String pin) {
        AuthResponse res;
        try {
            res = authClient.verifyPaymentPassword(
                    AuthRequest.builder()
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

    // [be] 다윤 260526 pg-payment-service 실결제 요청
    private void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        for (PinAndPayRequest.CardPortion card : request.getCards()) {
            PgAuthRequest pgRequest = PgAuthRequest.builder()
                    .payPaymentId(payment.getPaymentId())
                    .merchantId(payment.getMerchant_id())
                    .billingKey(String.valueOf(card.getCardId()))
                    .originalAmount(payment.getAmount())
                    .approvedAmount(card.getAmount())
                    .build();

            try {
                PgAuthResponse pgResponse = pgClient.pgPaymentRequest(
                        "Bearer server-test-token",
                        savedIdempotencyKey,
                        pgRequest);

                log.info("pgClientResponse : {}", pgResponse);

                if (pgResponse == null) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }

                payment.paidStatusUpdatePayment(LocalDateTime.now());

                EventEntity savedEvent = EventEntity.builder()
                        .payment_id(payment.getPaymentId())
                        .pg_txn_id(pgResponse.getPgTxnId())
                        .event_type(EventEntity.EventType.PAID)
                        .actor_type(EventEntity.ActorType.SYSTEM)
                        .created_at(pgResponse.getProcessedAt())
                        .build();

                eventRepository.save(savedEvent);

            } catch (FeignException e) {
                log.error("pg feign error. status={}, body={}", e.status(), e.contentUTF8());
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.BAD_REQUEST);
                }
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }

    // [be] 다윤 260526 멱등성 키 중복 체크 (userId + idempotencyKey)
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

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        return idempotencyKey.trim();
    }

    private CoreEntity.PaymentType parsePaymentType(String paymentType) {
        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

    }

    private void validateCardAmounts(PinAndPayRequest request) {
        long total = 0L;
        Set<Long> cardIds = new HashSet<>();
        for (PinAndPayRequest.CardPortion card : request.getCards()) {
            if (card.getCardId() == null || card.getAmount() == null || card.getAmount() <= 0) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            if (!cardIds.add(card.getCardId())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            total += card.getAmount();
        }

        if (total != request.getTotalAmount()) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void validatePrepareStatus(CoreEntity.PaymentStatus status) {
        if (status == CoreEntity.PaymentStatus.CREATED) {
            return;
        }
        if (status == CoreEntity.PaymentStatus.PAY_PENDING || status == CoreEntity.PaymentStatus.PG_PENDING) {
            throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        if (status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        throw new CustomException(ErrorCode.BAD_REQUEST);
    }

    private void validateRequestStatus(CoreEntity.PaymentStatus status) {
        if (status == CoreEntity.PaymentStatus.PAY_PENDING) {
            return;
        }
        if (status == CoreEntity.PaymentStatus.PG_PENDING) {
            throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        if (status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        throw new CustomException(ErrorCode.BAD_REQUEST);
    }
}
