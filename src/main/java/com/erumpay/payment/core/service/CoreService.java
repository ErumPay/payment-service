package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
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
import com.erumpay.payment.core.domain.dto.CanceledResponse;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.DutchMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.PrepareRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
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

import feign.FeignException;
import jakarta.persistence.EntityManager;
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
        CoreEntity payment = findPaymentOrThrow(request.getPaymentId());

        validatePaymentOwnerOrUnassigned(payment, userId);
        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = findReplayedPrepareResponse(
                userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        validateAmountMatches(payment.getAmount(), request.getAmount());
        coreValidationService.validatePrepareStatus(payment.getPayment_status());

        CoreEntity.PaymentType paymentType = coreValidationService.parsePaymentType(request.getPaymentType());
        LocalDateTime now = LocalDateTime.now();

        applyPrepareState(payment, normalizedIdempotencyKey, userId, paymentType, now);
        savePaymentWithDuplicateGuard(payment);
        createDutchHostSessionIfNeeded(request.getPaymentId(), userId, payment, paymentType);

        return finalizePrepare(payment, userId);
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 참여자
    public ResponseEntity<PrepareResponse> prepareMember(
            Long userId,
            String idempotencyKey,
            DutchMemberPrepareRequest request) {

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        return prepareDutchPayment(
                userId,
                normalizedIdempotencyKey,
                request,
                CoreEntity.DutchRole.MEMBER,
                CoreEntity.PaymentIntent.DUTCH_MEMBER_PAY,
                () -> validateDutchParticipantPayment(userId, normalizedIdempotencyKey, request),
                payment -> registerDutchParticipantPayment(userId, request, payment));
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 대표자
    public ResponseEntity<PrepareResponse> prepareHost(
            Long userId,
            String idempotencyKey,
            DutchMemberPrepareRequest request) {

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        return prepareDutchPayment(
                userId,
                normalizedIdempotencyKey,
                request,
                CoreEntity.DutchRole.HOST,
                CoreEntity.PaymentIntent.DUTCH_HOST_PAY,
                () -> {
                },
                payment -> {
                });
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<PinAndPayResponse> requestPay(Long userId, String idempotencyKey, PinAndPayRequest request) {
        log.info("/payment/request Service");

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        CoreEntity payment = findPaymentOrThrow(request.getPaymentId());

        validatePayRequestPreconditions(payment, userId, normalizedIdempotencyKey, request);

        // [be] 다윤 260526 auth-service pin 인증 요청
        // AuthResponse authResponse = verifyPin(userId, request.getPin());

        // [be] 다윤 260526 00:00 | pg-payment-service 실결제 요청
        corePgPaymentService.requestPgPayments(payment, request);

        // REQUIRES_NEW 트랜잭션에서 커밋된 최종 결제 상태를 응답에 반영한다.
        entityManager.refresh(payment);

        return ResponseEntity.ok(toPinAndPayResponse(payment));
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
            log.error("auth feign error. status={}, body={}", e.status(), e.contentUTF8());

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

        CoreEntity payment = findPaymentOrThrow(paymentId);
        validatePaymentOwnerOrUnassigned(payment, userId);

        if (payment.getPayment_status() == CoreEntity.PaymentStatus.CANCELED) {
            return toCanceledResponse(payment.getPaymentId(), payment.getPayment_status(), payment.getCanceled_at());
        }

        validateCancelableStatus(payment.getPayment_status());
        List<CardDetailEntity> cards = findCancelableCardsOrThrow(paymentId);
        cancelCardsInPg(payment, paymentId, normalizedIdempotencyKey, cards);

        LocalDateTime canceledAt = LocalDateTime.now();
        payment.voidedStatusUpdatePayment(canceledAt);

        return toCanceledResponse(payment.getPaymentId(), payment.getPayment_status(), canceledAt);
    }

    private CoreEntity findPaymentOrThrow(Long paymentId) {
        return coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));
    }

    private void validatePaymentOwnerOrUnassigned(CoreEntity payment, Long userId) {
        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private void validatePaymentOwner(CoreEntity payment, Long userId) {
        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private Optional<ResponseEntity<PrepareResponse>> findReplayedPrepareResponse(Long userId, String normalizedIdempotencyKey) {
        return coreValidationService.validateIdempotency(userId, normalizedIdempotencyKey);
    }

    private void validateAmountMatches(Long expectedAmount, Long requestedAmount) {
        if (!expectedAmount.equals(requestedAmount)) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }
    }

    private void applyPrepareState(
            CoreEntity payment,
            String normalizedIdempotencyKey,
            Long userId,
            CoreEntity.PaymentType paymentType,
            LocalDateTime now) {
        payment.preparePayment(
                normalizedIdempotencyKey,
                userId,
                paymentType,
                now);

        if (paymentType == CoreEntity.PaymentType.DUTCH) {
            payment.updatePaymentIntent(CoreEntity.PaymentIntent.DUTCH_HOST_AUTH_ONLY_PAY, now);
        }
    }

    private void savePaymentWithDuplicateGuard(CoreEntity payment) {
        try {
            coreRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
    }

    private void createDutchHostSessionIfNeeded(
            Long paymentId,
            Long userId,
            CoreEntity payment,
            CoreEntity.PaymentType paymentType) {
        if (paymentType != CoreEntity.PaymentType.DUTCH) {
            return;
        }

        DutchPayCreateResponse dutchResponse = dutchPayService.createSession(
                DutchPayCreateRequest.builder()
                        .host_payment_id(paymentId)
                        .host_user_id(userId)
                        .merchant_id(payment.getMerchant_id())
                        .total_amount(payment.getAmount())
                        .order_name(payment.getOrder_name())
                        .build());

        payment.hostDutchSessionPayment(dutchResponse.getSession_id(), CoreEntity.DutchRole.HOST);
    }

    private ResponseEntity<PrepareResponse> prepareDutchPayment(
            Long userId,
            String normalizedIdempotencyKey,
            DutchMemberPrepareRequest request,
            CoreEntity.DutchRole dutchRole,
            CoreEntity.PaymentIntent paymentIntent,
            Runnable preSaveValidation,
            DutchPaymentPostProcessor postSaveProcessor) {

        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = findReplayedPrepareResponse(
                userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        preSaveValidation.run();

        LocalDateTime now = LocalDateTime.now();
        DutchPaymentSaveOutcome saveOutcome = saveDutchPaymentWithIdempotencyGuard(
                userId,
                normalizedIdempotencyKey,
                request,
                now,
                dutchRole,
                paymentIntent);

        if (saveOutcome.hasReplayedResponse()) {
            return saveOutcome.getReplayedResponse();
        }

        CoreEntity payment = saveOutcome.getPayment();
        postSaveProcessor.process(payment);
        payment.payPendingStatusUpdatePayment(now);

        return finalizePrepare(payment, userId);
    }

    private void validateDutchParticipantPayment(
            Long userId,
            String normalizedIdempotencyKey,
            DutchMemberPrepareRequest request) {
        dutchPayService.validateParticipantPayment(
                request.getSessionId(),
                DutchPayParticipantPaymentValidateRequest.builder()
                        .participant_id(request.getParticipantId())
                        .user_id(userId)
                        .amount(request.getAmount())
                        .idempotency_key(normalizedIdempotencyKey)
                        .build());
    }

    private void registerDutchParticipantPayment(Long userId, DutchMemberPrepareRequest request, CoreEntity payment) {
        dutchPayService.registerParticipantPayment(
                request.getSessionId(),
                request.getParticipantId(),
                userId,
                payment);
    }

    private ResponseEntity<PrepareResponse> finalizePrepare(CoreEntity payment, Long userId) {
        requestAndPushRecommendation(payment, userId);
        return ResponseEntity.ok(toPrepareResponse(payment));
    }

    private void validatePayRequestPreconditions(
            CoreEntity payment,
            Long userId,
            String normalizedIdempotencyKey,
            PinAndPayRequest request) {
        validatePaymentOwner(payment, userId);
        validateIdempotencyKeyMatches(payment, normalizedIdempotencyKey);
        coreValidationService.validateRequestStatus(payment.getPayment_status());
        validateAmountMatches(payment.getAmount(), request.getTotalAmount());
        coreValidationService.validateCardAmounts(request);
        validateRemotePaymentRequestIfNeeded(payment);
    }

    private void validateIdempotencyKeyMatches(CoreEntity payment, String normalizedIdempotencyKey) {
        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || !savedIdempotencyKey.equals(normalizedIdempotencyKey)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateRemotePaymentRequestIfNeeded(CoreEntity payment) {
        if (payment.getPayment_type() == CoreEntity.PaymentType.REMOTE) {
            remotePayService.validatePaymentCanBeRequested(payment);
        }
    }

    private PinAndPayResponse toPinAndPayResponse(CoreEntity payment) {
        return PinAndPayResponse.builder()
                .paymentId(payment.getPaymentId())
                .userId(payment.getUserId())
                .paymentStatus(payment.getPayment_status().name())
                .paymentType(payment.getPayment_type().name())
                .build();
    }

    private void validateCancelableStatus(CoreEntity.PaymentStatus paymentStatus) {
        if (paymentStatus != CoreEntity.PaymentStatus.PAID) {
            throw new CustomException(ErrorCode.CANCELED_INVALID);
        }
    }

    private List<CardDetailEntity> findCancelableCardsOrThrow(Long paymentId) {
        List<CardDetailEntity> cards = cardDetailRepository.findCancelableCardsByPaymentId(paymentId);
        if (cards.isEmpty()) {
            throw new CustomException(ErrorCode.CANCELED_CARD_INVALID);
        }
        return cards;
    }

    private void cancelCardsInPg(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            List<CardDetailEntity> cards) {
        for (CardDetailEntity card : cards) {
            cancelSingleCardInPg(payment, paymentId, normalizedIdempotencyKey, card);
        }
    }

    private void cancelSingleCardInPg(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            CardDetailEntity card) {
        PgAuthPayResponse pgResponse = requestPgCancel(payment, paymentId, normalizedIdempotencyKey, card);
        validatePgCancelResponse(pgResponse);
    }

    private PgAuthPayResponse requestPgCancel(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            CardDetailEntity card) {
        PgPayCancelRequest cancelRequest = PgPayCancelRequest.builder()
                .payPaymentId(paymentId)
                .merchantId(payment.getMerchant_id())
                .cancelReason("USER_REQUEST")
                .build();

        String cancelIdempotencyKey = normalizedIdempotencyKey + "-" + card.getPg_txn_id();
        try {
            return pgClient.pgPaymentCancelRequest(
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
    }

    private void validatePgCancelResponse(PgAuthPayResponse pgResponse) {
        if (pgResponse == null || pgResponse.getStatus() == null) {
            throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
        }

        if (PG_STATUS_REJECTED.equals(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.CANCELED_PG_REJECTED);
        }
    }

    private CanceledResponse toCanceledResponse(
            Long paymentId,
            CoreEntity.PaymentStatus paymentStatus,
            LocalDateTime canceledAt) {
        return CanceledResponse.builder()
                .paymentId(paymentId)
                .paymentStatus(paymentStatus.name())
                .canceledAt(canceledAt)
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
            CoreEntity payment = coreRepository.saveAndFlush(
                    createDutchPaymentEntity(userId, normalizedIdempotencyKey, request, now, dutchRole, paymentIntent));
            return DutchPaymentSaveOutcome.saved(payment);
        } catch (DataIntegrityViolationException e) {
            Optional<ResponseEntity<PrepareResponse>> replayed = findReplayedPrepareResponse(userId, normalizedIdempotencyKey);
            if (replayed.isPresent()) {
                return DutchPaymentSaveOutcome.replayed(replayed.get());
            }
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
    }

    private CoreEntity createDutchPaymentEntity(
            Long userId,
            String normalizedIdempotencyKey,
            DutchMemberPrepareRequest request,
            LocalDateTime now,
            CoreEntity.DutchRole dutchRole,
            CoreEntity.PaymentIntent paymentIntent) {
        return CoreEntity.builder()
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
                .build();
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
            coreSseService.publishPaymentUpdated(
                    payment.getPaymentId(),
                    CoreSseEventType.RECOMMENDATION_SUCCEEDED,
                    recommendList);
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
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.RECOMMENDATION_FAILED,
                Map.of(
                        "paymentId", paymentId,
                        "status", status,
                        "reason", reason));
    }

    @FunctionalInterface
    private interface DutchPaymentPostProcessor {
        void process(CoreEntity payment);
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
