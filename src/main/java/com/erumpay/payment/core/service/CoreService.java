package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import feign.FeignException;
import jakarta.persistence.EntityManager;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.auth.AuthClient;
import com.erumpay.payment.core.client.auth.dto.AuthPinRequest;
import com.erumpay.payment.core.client.auth.dto.AuthPinResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.client.recommend.RecommendClient;
import com.erumpay.payment.core.client.recommend.dto.RecommendRequest;
import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;
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
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.service.DutchPayService;
import com.erumpay.payment.qr.service.QrService;
import com.erumpay.payment.remote.service.RemotePayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CoreService {
    private static final String AUTHORIZATION = "Bearer server-test-token";
    private static final String PG_STATUS_REJECTED = "REJECTED";
    private static final String RECOMMENDATION_STATUS_PENDING = "PENDING";
    private static final String RECOMMEND_EVENT_SUCCESS = "카드추천 조합";
    private static final String RECOMMEND_EVENT_FAILED = "카드추천 실패";

    private final PgClient pgClient;
    private final CardDetailRepository cardDetailRepository;
    private final CoreRepository coreRepository;
    private final CoreValidationService coreValidationService;
    private final CorePgPaymentService corePgPaymentService;
    private final AuthClient authClient;
    private final DutchPayService dutchPayService;
    private final QrService qrService;
    private final RemotePayService remotePayService;
    private final EntityManager entityManager;
    private final RecommendClient recommendClient;
    private final CoreSseService coreSseService;

    // [be] 다윤 260526 결제 요청 시작 - 개인, 더치페이 대표자
    public ResponseEntity<PrepareResponse> preparePay(Long userId, String idempotencyKey, PrepareRequest request) {
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

        if (paymentType == CoreEntity.PaymentType.DUTCH) {
            payment.updatePaymentIntent(CoreEntity.PaymentIntent.DUTCH_HOST_AUTH_ONLY_PAY, now);
        }

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

            payment.hostDutchSessionPayment(dutchResponse.getSession_id(), CoreEntity.DutchRole.HOST);
        }

        requestAndPushRecommendation(payment, userId);
        return ResponseEntity.ok(toPrepareResponse(payment));
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 참여자
    public ResponseEntity<PrepareResponse> prepareMember(
            Long userId,
            String idempotencyKey,
            DutchMemberPrepareRequest request) {

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        // [be] 다윤 260528 00:00 | 멱등성 사전 체크
        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = coreValidationService.validateIdempotency(userId,
                normalizedIdempotencyKey);

        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        // [be] 다윤 260529 13:00 | 더치 세션/참여자/금액을 먼저 검증해 주문 생성 직후 롤백되는 케이스를 줄인다.
        dutchPayService.validateParticipantPayment(
                request.getSessionId(),
                DutchPayParticipantPaymentValidateRequest.builder()
                        .participant_id(request.getParticipantId())
                        .user_id(userId)
                        .amount(request.getAmount())
                        .idempotency_key(normalizedIdempotencyKey)
                        .build());

        LocalDateTime now = LocalDateTime.now();
        DutchPaymentSaveOutcome memberSaveOutcome = saveDutchPaymentWithIdempotencyGuard(
                userId,
                normalizedIdempotencyKey,
                request,
                now,
                CoreEntity.DutchRole.MEMBER,
                CoreEntity.PaymentIntent.DUTCH_MEMBER_PAY);

        if (memberSaveOutcome.hasReplayedResponse()) {
            return memberSaveOutcome.getReplayedResponse();
        }

        CoreEntity payment = memberSaveOutcome.getPayment();

        dutchPayService.registerParticipantPayment(
                request.getSessionId(),
                request.getParticipantId(),
                userId,
                payment);

        payment.payPendingStatusUpdatePayment(LocalDateTime.now());

        requestAndPushRecommendation(payment, userId);

        return ResponseEntity.ok(toPrepareResponse(payment));
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 대표자
    public ResponseEntity<PrepareResponse> prepareHost(
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
        DutchPaymentSaveOutcome hostSaveOutcome = saveDutchPaymentWithIdempotencyGuard(
                userId,
                normalizedIdempotencyKey,
                request,
                now,
                CoreEntity.DutchRole.HOST,
                CoreEntity.PaymentIntent.DUTCH_HOST_PAY);
        if (hostSaveOutcome.hasReplayedResponse()) {
            return hostSaveOutcome.getReplayedResponse();
        }
        CoreEntity payment = hostSaveOutcome.getPayment();

        payment.payPendingStatusUpdatePayment(LocalDateTime.now());

        requestAndPushRecommendation(payment, userId);

        return ResponseEntity.ok(toPrepareResponse(payment));
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<PinAndPayResponse> requestPay(Long userId, String idempotencyKey, PinAndPayRequest request) {

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
        if (payment.getPayment_type() == CoreEntity.PaymentType.REMOTE) {
            remotePayService.validatePaymentCanBeRequested(payment);
        }

        // [be] 다윤 260526 auth-service pin 인증 요청
        // AuthResponse authResponse = verifyPin(userId, request.getPin());

        // [be] 다윤 260526 00:00 | pg-payment-service 실결제 요청
        corePgPaymentService.requestPgPayments(payment, request);

        // REQUIRES_NEW 트랜잭션에서 커밋된 최종 결제 상태를 응답에 반영한다.
        entityManager.refresh(payment);

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

    // [be] 다윤 260527 일반 결제 취소
    public CanceledResponse cancelPay(Long userId, String idempotencyKey, Long paymentId) {

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

        if (payment.getPayment_status() == CoreEntity.PaymentStatus.CANCELED) {
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

    private PrepareResponse toPrepareResponse(CoreEntity payment) {
        return PrepareResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(payment.getPayment_status() == null ? null : payment.getPayment_status().name())
                .recommendationStatus(RECOMMENDATION_STATUS_PENDING)
                .paymentType(payment.getPayment_type() == null ? null : payment.getPayment_type().name())
                .paymentIntent(payment.getPayment_intent() == null ? null : payment.getPayment_intent().name())
                .dutchRole(payment.getDutch_role() == null ? null : payment.getDutch_role().name())
                .dutchSessionId(payment.getDutch_session_id())
                .amount(payment.getAmount())
                .build();
    }

    private DutchPaymentSaveOutcome saveDutchPaymentWithIdempotencyGuard(
            Long userId,
            String normalizedIdempotencyKey,
            DutchMemberPrepareRequest request,
            LocalDateTime now,
            CoreEntity.DutchRole dutchRole,
            CoreEntity.PaymentIntent paymentIntent) {
        try {
            CoreEntity payment = coreRepository.saveAndFlush(CoreEntity.builder()
                    .userId(userId)
                    .merchant_id(request.getMerchantId())
                    .idempotencyKey(normalizedIdempotencyKey)
                    .order_no(qrService.generateUniqueOrderNo(now))
                    .order_name(request.getOrderName())
                    .amount(request.getAmount())
                    .payment_status(CoreEntity.PaymentStatus.CREATED)
                    .payment_type(CoreEntity.PaymentType.DUTCH)
                    .channel_type(CoreEntity.ChannelType.OFFLINE)
                    .dutch_role(dutchRole)
                    .payment_intent(paymentIntent)
                    .dutch_session_id(request.getSessionId())
                    .updated_at(now)
                    .created_at(now)
                    .build());

            return DutchPaymentSaveOutcome.saved(payment);
        } catch (DataIntegrityViolationException e) {
            Optional<ResponseEntity<PrepareResponse>> replayed = coreValidationService.validateIdempotency(userId,
                    normalizedIdempotencyKey);
            if (replayed.isPresent()) {
                return DutchPaymentSaveOutcome.replayed(replayed.get());
            }
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
    }

    // [be] 다윤 260529 13:00 | 결제 카드추천 조합 요청
    private void requestAndPushRecommendation(CoreEntity payment, Long userId) {
        try {
            RecommendResponse recommendList = recommendClient.recommentListRequest(
                    RecommendRequest.builder()
                            .paymentId(payment.getPaymentId())
                            .userId(userId)
                            // .merchantName(payment.getMerchant_name())
                            .merchantName("스타벅스 강남점")
                            .mccCode("5811")
                            .amount(payment.getAmount())
                            .build());

            if (recommendList == null || recommendList.getResults() == null) {
                log.warn("recommend response is empty. paymentId={}, userId={}", payment.getPaymentId(), userId);
                return;
            }

            log.info("recommend list response: {}", recommendList);
            coreSseService.pushEvent(payment.getPaymentId(), RECOMMEND_EVENT_SUCCESS, recommendList);
        } catch (FeignException e) {
            log.error("recommend feign error. paymentId={}, userId={}, status={}, body={}",
                    payment.getPaymentId(), userId, e.status(), e.contentUTF8());
            pushRecommendFailedEvent(payment.getPaymentId(), e.status(), "추천 서비스 호출 실패");
        } catch (Exception e) {
            log.error("recommend request unexpected error. paymentId={}, userId={}",
                    payment.getPaymentId(), userId, e);
            pushRecommendFailedEvent(payment.getPaymentId(), 500, "추천 처리 중 오류");
        }
    }

    private void pushRecommendFailedEvent(Long paymentId, int status, String reason) {
        coreSseService.pushEvent(paymentId, RECOMMEND_EVENT_FAILED, Map.of(
                "paymentId", paymentId,
                "eventType", "RECOMMENDATION_FAILED",
                "status", status,
                "reason", reason));
    }

    private static final class DutchPaymentSaveOutcome {
        private final CoreEntity payment;
        private final ResponseEntity<PrepareResponse> replayedResponse;

        private DutchPaymentSaveOutcome(CoreEntity payment, ResponseEntity<PrepareResponse> replayedResponse) {
            this.payment = payment;
            this.replayedResponse = replayedResponse;
        }

        private static DutchPaymentSaveOutcome saved(CoreEntity payment) {
            return new DutchPaymentSaveOutcome(payment, null);
        }

        private static DutchPaymentSaveOutcome replayed(ResponseEntity<PrepareResponse> replayedResponse) {
            return new DutchPaymentSaveOutcome(null, replayedResponse);
        }

        private CoreEntity getPayment() {
            return payment;
        }

        private ResponseEntity<PrepareResponse> getReplayedResponse() {
            return replayedResponse;
        }

        private boolean hasReplayedResponse() {
            return replayedResponse != null;
        }
    }

    // [be] 다윤 260522 SSE 연결 가능 여부 판단
    @Transactional(readOnly = true)
    public boolean userCanAccess(Long paymentId, Long userId) {
        return coreRepository.existsByPaymentIdAndUserId(paymentId, userId);
    }
}
