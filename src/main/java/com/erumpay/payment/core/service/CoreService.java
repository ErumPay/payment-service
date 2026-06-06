package com.erumpay.payment.core.service;

import java.time.Duration;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.auth.AuthClient;
import com.erumpay.payment.core.client.auth.AuthFeignErrorMapper;
import com.erumpay.payment.core.client.auth.dto.AuthPinRequest;
import com.erumpay.payment.core.client.auth.dto.AuthPinResponse;
import com.erumpay.payment.core.client.card.CardClient;
import com.erumpay.payment.core.client.card.CardFeignErrorMapper;
import com.erumpay.payment.core.client.card.dto.PaymentResultRequest;
import com.erumpay.payment.core.client.card.dto.PaymentResultResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.PgFeignErrorMapper;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.client.pg.dto.PgSplitPayResponse;
import com.erumpay.payment.core.client.recommend.RecommendClient;
import com.erumpay.payment.core.client.recommend.RecommendFeignErrorMapper;
import com.erumpay.payment.core.client.recommend.dto.RecommendRequest;
import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;
import com.erumpay.payment.core.dao.CardDetailRepository;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.CanceledResponse;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.DutchMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.PaymentDetailResponse;
import com.erumpay.payment.core.domain.dto.PaymentAllFetchRequest;
import com.erumpay.payment.core.domain.dto.PaymentAllFetchResponse;
import com.erumpay.payment.core.domain.dto.PaymentListResonse;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayResponse;
import com.erumpay.payment.core.domain.dto.PrepareRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.dto.RemoteMemberPrepareRequest;
import com.erumpay.payment.core.domain.dto.UserWithdrawalResponse;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CardDetailEntity.CardStatus;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.service.DutchPayService;
import com.erumpay.payment.qr.service.QrService;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayDraftCreateRequest;
import com.erumpay.payment.remote.service.RemotePayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CoreService {
    private static final String CARD_EVENT_CANCELED = "CANCELED";
    private static final String PG_STATUS_REJECTED = "REJECTED";
    private static final String PG_STATUS_CANCELLED = "CANCELLED";
    private static final String PG_STATUS_FAILED = "FAILED";
    private static final String PG_STATUS_COMPENSATION_REQUIRED = "COMPENSATION_REQUIRED";
    private static final String PG_ERROR_ALREADY_CANCELLED = "PAYMENT_ALREADY_CANCELLED";
    private static final String SPLIT_CANCEL_REASON = "사용자 요청으로 분할결제 전체 취소";
    private static final String RECOMMENDATION_STATUS_PENDING = "PENDING";
    private static final String RECOMMENDATION_CACHE_KEY_PREFIX = "payment:recommendation:";
    private static final Duration RECOMMENDATION_CACHE_TTL = Duration.ofMinutes(30);
    private static final ZoneId PAYMENT_HISTORY_BUSINESS_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<CoreEntity.PaymentStatus> WITHDRAWAL_BLOCKING_STATUSES = List.of(
            CoreEntity.PaymentStatus.PAY_PENDING,
            CoreEntity.PaymentStatus.PG_PENDING,
            CoreEntity.PaymentStatus.CANCEL_REQUESTED,
            CoreEntity.PaymentStatus.AUTHORIZED);
    private static final List<CoreEntity.PaymentStatus> PAYMENT_HISTORY_STATUSES = List.of(
            CoreEntity.PaymentStatus.CANCEL_REQUESTED,
            CoreEntity.PaymentStatus.PAID,
            CoreEntity.PaymentStatus.CANCELED);

    private final PgClient pgClient;
    private final CardClient cardClient;
    private final CardFeignErrorMapper cardFeignErrorMapper;
    private final CardDetailRepository cardDetailRepository;
    private final CoreRepository coreRepository;
    private final CoreValidationService coreValidationService;
    private final CorePgPaymentService corePgPaymentService;
    private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;
    private final AuthClient authClient;
    private final AuthFeignErrorMapper authFeignErrorMapper;
    private final DutchPayService dutchPayService;
    private final QrService qrService;
    private final RemotePayService remotePayService;
    private final EntityManager entityManager;
    private final RecommendClient recommendClient;
    private final RecommendFeignErrorMapper recommendFeignErrorMapper;
    private final CoreSseService coreSseService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PgFeignErrorMapper pgFeignErrorMapper;

    @Value("${pg.authorization}")
    private String pgAuthorization;

    // [be] 다윤 260526 결제 요청 시작 - 개인, 더치페이 대표자, 원격 요청자
    public ResponseEntity<PrepareResponse> preparePay(Long userId, String idempotencyKey, PrepareRequest request) {
        log.info("/payment/prepare Service");

        if (request == null) {
            throw new CustomException(ErrorCode.PAYMENT_REQUEST_BODY_INVALID);
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
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
        createRemoteDraftIfNeeded(userId, payment, paymentType, now);

        return finalizePrepare(payment, userId);
    }

    // [be] 다윤 260526 결제 요청 시작 - 더치페이 참여자
    public ResponseEntity<PrepareResponse> prepareMember(
            Long userId,
            String idempotencyKey,
            DutchMemberPrepareRequest request) {

        if (request == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
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

        if (request == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        return prepareDutchPayment(
                userId,
                normalizedIdempotencyKey,
                request,
                CoreEntity.DutchRole.HOST,
                CoreEntity.PaymentIntent.DUTCH_HOST_PAY,
                () -> validateDutchHostFinalPayment(userId, request),
                payment -> {
                });
    }

    // [be] 다윤 260605 20:00 | 결제 요청 시작 - 원격결제 대리자
    public ResponseEntity<PrepareResponse> prepareProxy(Long userId, String idempotencyKey,
            RemoteMemberPrepareRequest request) {

        if (request == null) {
            throw new CustomException(ErrorCode.RMT_INVALID_REQUEST);
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = findReplayedPrepareResponse(
                userId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        LocalDateTime now = LocalDateTime.now();
        DutchPaymentSaveOutcome saveOutcome = saveRemoteDeputyPaymentWithIdempotencyGuard(
                userId,
                normalizedIdempotencyKey,
                request,
                now);
        if (saveOutcome.hasReplayedResponse()) {
            return saveOutcome.getReplayedResponse();
        }

        CoreEntity payment = saveOutcome.getPayment();
        RemotePayCreateResponse remoteResponse = remotePayService.connectPaymentForPrepare(
                userId,
                request.getRemoteRequestId(),
                payment);
        log.info("remote connectPaymentForPrepare response : {}", remoteResponse);

        payment.connectRemoteRequest(remoteResponse.getRequest_id(), now);
        payment.payPendingStatusUpdatePayment(now);

        return finalizePrepare(payment, userId);
    }

    // [be] 다윤 260526 비밀번호 확인 및 실결제 요청
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<PinAndPayResponse> requestPay(Long userId, String idempotencyKey, PinAndPayRequest request) {
        log.info("/payment/request Service");

        if (request == null) {
            throw new CustomException(ErrorCode.PAYMENT_REQUEST_BODY_INVALID);
        }
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        CoreEntity payment = findPaymentOrThrow(request.getPaymentId());

        validatePayRequestPreconditions(payment, userId, normalizedIdempotencyKey, request);

        // [be] 다윤 260526 auth-service pin 인증 요청
        verifyPin(userId, request.getPin());

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
            throw new CustomException(authFeignErrorMapper.mapVerifyPinError(e), e);
        }

        if (res == null) {
            throw new CustomException(ErrorCode.AUTH_RESPONSE_INVALID);
        }
        if (!res.isVerified()) {
            throw new CustomException(ErrorCode.PIN_VERIFY_FAILED);
        }

        return res;
    }

    // [be] 다윤 260527 일반 결제 취소
    public CanceledResponse cancelPay(Long userId, String idempotencyKey, Long paymentId) {
        log.info("/payment/cancel Service");
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);

        CoreEntity payment = findPaymentOrThrow(paymentId);
        validatePaymentOwnerOrUnassigned(payment, userId);

        if (payment.getPayment_status() == CoreEntity.PaymentStatus.CANCELED) {
            return toCanceledResponse(payment.getPaymentId(), payment.getPayment_status(), payment.getCanceledAt());
        }

        validateCancelableStatus(payment.getPayment_status());
        List<CardDetailEntity> cards = findCancelableCardsOrThrow(paymentId);
        cancelCardsInPg(payment, paymentId, normalizedIdempotencyKey, cards);

        LocalDateTime canceledAt = LocalDateTime.now();
        payment.voidedStatusUpdatePayment(canceledAt);
        notifyCardPaymentCanceled(payment, cards, canceledAt);

        return toCanceledResponse(payment.getPaymentId(), payment.getPayment_status(), canceledAt);
    }

    // [be] 다윤 260602 10:00 | 결제 내역 전체 조회
    @Transactional(readOnly = true)
    public PaymentListResonse getAllPayments(
            Long userId,
            int page,
            String status,
            String period,
            LocalDate start,
            LocalDate end,
            String paymentType,
            String strategyType) {
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        if (page < 0) {
            throw new CustomException(ErrorCode.PAYMENT_PAGE_INVALID);
        }

        int size = 20;
        Pageable pageable = buildPaymentPageable(page, size);
        List<CoreEntity.PaymentStatus> paymentStatuses = resolvePaymentListStatuses(status);
        PaymentHistoryDateRange dateRange = resolvePaymentHistoryDateRange(period, start, end);
        CoreEntity.PaymentType resolvedPaymentType = resolvePaymentHistoryPaymentType(paymentType);
        CoreEntity.StrategyType resolvedStrategyType = resolvePaymentHistoryStrategyType(strategyType);
        Slice<CoreEntity> paymentSlice = coreRepository.findAllByUserIdAndPaymentStatuses(
                userId,
                paymentStatuses,
                dateRange.from(),
                dateRange.to(),
                resolvedPaymentType,
                resolvedStrategyType,
                pageable);

        List<PaymentListResonse.PaymentItem> items = paymentSlice.getContent().stream()
                .map(this::toPaymentItem)
                .toList();

        return PaymentListResonse.builder()
                .items(items)
                .page((long) paymentSlice.getNumber())
                .count((long) items.size())
                .hasNext(paymentSlice.hasNext())
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentAllFetchResponse getRecommendationUsageSummary(
            Long userId,
            PaymentAllFetchRequest request) {
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        if (request == null) {
            throw new CustomException(ErrorCode.PAYMENT_REQUEST_BODY_INVALID);
        }
        if (request.getFrom() == null || request.getTo() == null || request.getFrom().isAfter(request.getTo())) {
            throw new CustomException(ErrorCode.PAYMENT_DATE_RANGE_INVALID);
        }

        LocalDateTime fromDateTime = request.getFrom().atStartOfDay();
        LocalDateTime toDateTime = request.getTo().plusDays(1).atStartOfDay();

        CoreRepository.PaymentUsageTotalProjection total = coreRepository.findPaymentUsageTotal(
                userId,
                fromDateTime,
                toDateTime,
                CoreEntity.PaymentStatus.PAID);

        return PaymentAllFetchResponse.builder()
                .userId(userId)
                .from(request.getFrom())
                .to(request.getTo())
                .totalAmount(total == null ? 0L : nullToZero(total.getTotalAmount()))
                .paymentCount(total == null ? 0L : nullToZero(total.getPaymentCount()))
                .merchantUsages(coreRepository.findMerchantUsages(
                        userId,
                        fromDateTime,
                        toDateTime,
                        CoreEntity.PaymentStatus.PAID).stream()
                        .map(this::toMerchantUsage)
                        .toList())
                .cardUsages(cardDetailRepository.findCardUsages(
                        userId,
                        fromDateTime,
                        toDateTime,
                        CoreEntity.PaymentStatus.PAID,
                        CardStatus.PAID).stream()
                        .map(this::toCardUsage)
                        .toList())
                .build();
    }

    // [be] 다윤 260602 10:00 | 결제 내역 단일 조회
    @Transactional(readOnly = true)
    public PaymentDetailResponse getDetailPayment(Long userId, Long paymentId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }
        if (paymentId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        CoreEntity payment = findPaymentOrThrow(paymentId);
        validatePaymentOwner(payment, userId);
        validatePaymentHistoryStatus(payment);
        List<CardDetailEntity> cardDetails = cardDetailRepository.findAllByPaymentId(paymentId);

        return toPaymentDetailResponse(payment, cardDetails);
    }

    // [be] 다윤 260605 20:00 | 사용자 미결제건 조회
    @Transactional(readOnly = true)
    public UserWithdrawalResponse getWithdrawalValidate(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.PAYMENT_USER_REQUIRED);
        }

        long unpaidPaymentCount = coreRepository.countByUserIdAndPaymentStatuses(
                userId,
                WITHDRAWAL_BLOCKING_STATUSES);
        boolean possible = unpaidPaymentCount == 0;

        return UserWithdrawalResponse.builder()
                .possibility(possible)
                .userId(userId)
                .hasUnpaidPayments(!possible)
                .unpaidPaymentCount(unpaidPaymentCount)
                .message(possible
                        ? "탈퇴 가능합니다."
                        : "미결제 또는 처리 중인 결제 건이 있어 탈퇴할 수 없습니다.")
                .build();
    }

    private Pageable buildPaymentPageable(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "paidAt")
                        .and(Sort.by(Sort.Direction.DESC, "paymentId")));
    }

    private PaymentHistoryDateRange resolvePaymentHistoryDateRange(String period, LocalDate start, LocalDate end) {
        if (period != null && (start != null || end != null)) {
            throw new CustomException(ErrorCode.PAYMENT_PERIOD_INVALID);
        }

        if (start != null || end != null) {
            if (start == null || end == null || start.isAfter(end)) {
                throw new CustomException(ErrorCode.PAYMENT_DATE_RANGE_INVALID);
            }
            return new PaymentHistoryDateRange(start.atStartOfDay(), end.plusDays(1).atStartOfDay());
        }

        if (period == null || period.isBlank() || "ALL".equalsIgnoreCase(period.trim())) {
            return new PaymentHistoryDateRange(null, null);
        }

        LocalDate today = LocalDate.now(PAYMENT_HISTORY_BUSINESS_ZONE);
        LocalDate from = switch (period.trim().toUpperCase()) {
            case "WEEK" -> today.with(DayOfWeek.MONDAY);
            case "MONTH" -> today.withDayOfMonth(1);
            case "YEAR" -> today.withDayOfYear(1);
            default -> throw new CustomException(ErrorCode.PAYMENT_PERIOD_INVALID);
        };

        return new PaymentHistoryDateRange(
                toPaymentHistoryStartOfDay(from),
                toPaymentHistoryStartOfDay(today.plusDays(1)));
    }

    private LocalDateTime toPaymentHistoryStartOfDay(LocalDate date) {
        return date.atStartOfDay(PAYMENT_HISTORY_BUSINESS_ZONE).toLocalDateTime();
    }

    private CoreEntity.PaymentType resolvePaymentHistoryPaymentType(String paymentType) {
        if (paymentType == null || paymentType.isBlank() || "ALL".equalsIgnoreCase(paymentType.trim())) {
            return null;
        }

        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.PAYMENT_TYPE_FILTER_INVALID, e);
        }
    }

    private CoreEntity.StrategyType resolvePaymentHistoryStrategyType(String strategyType) {
        if (strategyType == null || strategyType.isBlank() || "ALL".equalsIgnoreCase(strategyType.trim())) {
            return null;
        }

        try {
            return CoreEntity.StrategyType.valueOf(strategyType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.PAYMENT_STRATEGY_TYPE_INVALID, e);
        }
    }

    private List<CoreEntity.PaymentStatus> resolvePaymentListStatuses(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status.trim())) {
            return PAYMENT_HISTORY_STATUSES;
        }

        return switch (status.trim().toUpperCase()) {
            case "PAID" -> List.of(CoreEntity.PaymentStatus.PAID);
            case "CANCELED" -> List.of(CoreEntity.PaymentStatus.CANCELED);
            default -> throw new CustomException(ErrorCode.PAYMENT_STATUS_FILTER_INVALID);
        };
    }

    private void validatePaymentHistoryStatus(CoreEntity payment) {
        if (!PAYMENT_HISTORY_STATUSES.contains(payment.getPayment_status())) {
            throw new CustomException(ErrorCode.PAY_NOT_FOUND);
        }
    }

    private CoreEntity findPaymentOrThrow(Long paymentId) {
        return coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));
    }

    private void validatePaymentOwnerOrUnassigned(CoreEntity payment, Long userId) {
        if (payment.getUserId() != null && !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PAYMENT_OWNER_MISMATCH);
        }
    }

    private void validatePaymentOwner(CoreEntity payment, Long userId) {
        if (payment.getUserId() == null || !payment.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.PAYMENT_OWNER_MISMATCH);
        }
    }

    private Optional<ResponseEntity<PrepareResponse>> findReplayedPrepareResponse(Long userId,
            String normalizedIdempotencyKey) {
        return coreValidationService.validateIdempotency(userId, normalizedIdempotencyKey);
    }

    private void validateAmountMatches(Long expectedAmount, Long requestedAmount) {
        if (expectedAmount == null || requestedAmount == null) {
            throw new CustomException(ErrorCode.AMOUNT_MISMATCH);
        }
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

    private void createRemoteDraftIfNeeded(
            Long userId,
            CoreEntity payment,
            CoreEntity.PaymentType paymentType,
            LocalDateTime now) {
        if (paymentType != CoreEntity.PaymentType.REMOTE) {
            return;
        }

        RemotePayCreateResponse remoteResponse = remotePayService.createDraftFromCore(
                userId,
                RemotePayDraftCreateRequest.builder()
                        .source_payment_id(payment.getPaymentId())
                        .description(payment.getOrder_name())
                        .build());

        log.info("remote createDraftFromCore response : {}", remoteResponse);
        payment.connectRemoteRequest(remoteResponse.getRequest_id(), now);
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
                        .user_id(userId)
                        .amount(request.getAmount())
                        .idempotency_key(normalizedIdempotencyKey)
                        .build());
    }

    private void registerDutchParticipantPayment(Long userId, DutchMemberPrepareRequest request, CoreEntity payment) {
        dutchPayService.registerParticipantPayment(
                request.getSessionId(),
                userId,
                payment);
    }

    private void validateDutchHostFinalPayment(Long userId, DutchMemberPrepareRequest request) {
        dutchPayService.validateHostFinalPayment(
                request.getSessionId(),
                userId,
                request.getAmount());
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
        if (request.getPin() == null || request.getPin().isBlank()) {
            throw new CustomException(ErrorCode.PAYMENT_PIN_REQUIRED);
        }
        validateAmountMatches(payment.getAmount(), request.getTotalAmount());
        coreValidationService.validateCardAmounts(request);
        validateRemotePaymentRequestIfNeeded(payment);
    }

    private void validateIdempotencyKeyMatches(CoreEntity payment, String normalizedIdempotencyKey) {
        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || !savedIdempotencyKey.equals(normalizedIdempotencyKey)) {
            throw new CustomException(ErrorCode.PAYMENT_IDEMPOTENCY_KEY_MISMATCH);
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
        if (payment.getPgGroupId() != null) {
            cancelSplitCardsInPg(payment, paymentId, normalizedIdempotencyKey, payment.getPgGroupId(), cards);
            return;
        }

        for (CardDetailEntity card : cards) {
            cancelSingleCardInPg(payment, paymentId, normalizedIdempotencyKey, card);
        }
    }

    private void cancelSplitCardsInPg(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            Long pgGroupId,
            List<CardDetailEntity> cards) {
        cards.forEach(card -> corePgPaymentPersistenceService.markCardCancelRequested(card.getPayment_card_id()));
        try {
            PgSplitPayResponse pgResponse = requestPgSplitCancel(
                    payment,
                    paymentId,
                    normalizedIdempotencyKey,
                    pgGroupId);
            validatePgSplitCancelResponse(pgResponse);
            LocalDateTime canceledAt = LocalDateTime.now();
            corePgPaymentPersistenceService.markCardsCanceled(paymentCardIds(cards), canceledAt);
        } catch (RuntimeException e) {
            markSplitCardCancelFailed(payment.getPaymentId(), cards, e);
            throw e;
        }
    }

    private List<Long> paymentCardIds(List<CardDetailEntity> cards) {
        return cards.stream()
                .map(CardDetailEntity::getPayment_card_id)
                .toList();
    }

    private void markSplitCardCancelFailed(
            Long paymentId,
            List<CardDetailEntity> cards,
            RuntimeException cause) {
        for (CardDetailEntity card : cards) {
            try {
                corePgPaymentPersistenceService.markCardCancelFailed(card.getPayment_card_id());
            } catch (RuntimeException statusException) {
                log.error(
                        "split card cancel failed status update failed. paymentId={}, paymentCardId={}, alert=CANCEL_REQUESTED_MAY_REMAIN",
                        paymentId,
                        card.getPayment_card_id(),
                        statusException);
            }
        }
        log.error("split card cancel failed. paymentId={}, paymentCardIds={}",
                paymentId,
                paymentCardIds(cards),
                cause);
    }

    private void cancelSingleCardInPg(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            CardDetailEntity card) {
        corePgPaymentPersistenceService.markCardCancelRequested(card.getPayment_card_id());
        try {
            PgAuthPayResponse pgResponse = requestPgCancel(payment, paymentId, normalizedIdempotencyKey, card);
            validatePgCancelResponse(pgResponse);
            corePgPaymentPersistenceService.markCardCanceled(card.getPayment_card_id(), LocalDateTime.now());
        } catch (RuntimeException e) {
            corePgPaymentPersistenceService.markCardCancelFailed(card.getPayment_card_id());
            throw e;
        }
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
                    pgAuthorization,
                    cancelIdempotencyKey,
                    card.getPg_txn_id(),
                    cancelRequest);
        } catch (FeignException e) {
            log.error("pg cancel feign error. status={}, body={}", e.status(), e.contentUTF8());
            if (isPgAlreadyCancelled(e)) {
                return alreadyCancelledPgResponse(payment, paymentId, card);
            }
            throw new CustomException(
                    pgFeignErrorMapper.map(e, ErrorCode.CANCELED_PG_REJECTED, ErrorCode.INTERNAL_PG_SERVER_ERROR),
                    e);
        }
    }

    private PgSplitPayResponse requestPgSplitCancel(
            CoreEntity payment,
            Long paymentId,
            String normalizedIdempotencyKey,
            Long pgGroupId) {
        PgPayCancelRequest cancelRequest = PgPayCancelRequest.builder()
                .payPaymentId(paymentId)
                .merchantId(payment.getMerchant_id())
                .cancelReason(SPLIT_CANCEL_REASON)
                .build();

        try {
            return pgClient.pgSplitPaymentCancelRequest(
                    pgAuthorization,
                    normalizedIdempotencyKey,
                    pgGroupId,
                    cancelRequest);
        } catch (FeignException e) {
            log.error("pg split cancel feign error. status={}", e.status());
            if (isPgAlreadyCancelled(e)) {
                return alreadyCancelledPgSplitResponse(payment, paymentId, pgGroupId);
            }
            throw new CustomException(
                    pgFeignErrorMapper.map(e, ErrorCode.CANCELED_PG_REJECTED, ErrorCode.INTERNAL_PG_SERVER_ERROR),
                    e);
        }
    }

    private void validatePgCancelResponse(PgAuthPayResponse pgResponse) {
        if (pgResponse == null || pgResponse.getStatus() == null) {
            throw new CustomException(ErrorCode.PG_RESPONSE_INVALID);
        }

        if (PG_STATUS_REJECTED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.CANCELED_PG_REJECTED);
        }

        if (PG_STATUS_FAILED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.PG_CANCEL_FAILED);
        }

        if (!PG_STATUS_CANCELLED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.PG_PAYMENT_STATUS_INVALID);
        }
    }

    private void validatePgSplitCancelResponse(PgSplitPayResponse pgResponse) {
        if (pgResponse == null || pgResponse.getStatus() == null) {
            throw new CustomException(ErrorCode.PG_RESPONSE_INVALID);
        }

        if (PG_STATUS_REJECTED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.CANCELED_PG_REJECTED);
        }

        if (PG_STATUS_FAILED.equalsIgnoreCase(pgResponse.getStatus())
                || PG_STATUS_COMPENSATION_REQUIRED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.PG_COMPENSATION_CANCEL_FAILED);
        }

        if (!PG_STATUS_CANCELLED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.PG_PAYMENT_STATUS_INVALID);
        }
    }

    private boolean isPgAlreadyCancelled(FeignException e) {
        return e.status() == 409 && e.contentUTF8() != null && e.contentUTF8().contains(PG_ERROR_ALREADY_CANCELLED);
    }

    private PgAuthPayResponse alreadyCancelledPgResponse(CoreEntity payment, Long paymentId, CardDetailEntity card) {
        return PgAuthPayResponse.builder()
                .pgTxnId(card.getPg_txn_id())
                .payPaymentId(paymentId)
                .merchantId(payment.getMerchant_id())
                .status(PG_STATUS_CANCELLED)
                .build();
    }

    private PgSplitPayResponse alreadyCancelledPgSplitResponse(CoreEntity payment, Long paymentId, Long pgGroupId) {
        return PgSplitPayResponse.builder()
                .pgGroupId(pgGroupId)
                .payPaymentId(paymentId)
                .merchantId(payment.getMerchant_id())
                .totalAmount(payment.getAmount())
                .status(PG_STATUS_CANCELLED)
                .items(List.of())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    private void notifyCardPaymentCanceled(
            CoreEntity payment,
            List<CardDetailEntity> cards,
            LocalDateTime canceledAt) {
        if (payment.getUserId() == null) {
            log.error("card payment result notify skipped. paymentId={}, eventType={}, userId=null",
                    payment.getPaymentId(),
                    CARD_EVENT_CANCELED);
            return;
        }

        try {
            PaymentResultRequest request = PaymentResultRequest.builder()
                    .paymentId(payment.getPaymentId())
                    .eventType(CARD_EVENT_CANCELED)
                    .occurredAt(canceledAt)
                    .cards(cards.stream()
                            .map(this::toCanceledPaymentResultCard)
                            .toList())
                    .build();

            logCardPaymentResultRequest(request);
            PaymentResultResponse response = cardClient.paymentResultSend(payment.getUserId(), request);
            log.info(
                    "card payment result notify success. paymentId={}, userId={}, eventType={}, applied={}, appliedCardCount={}, reason={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    CARD_EVENT_CANCELED,
                    response == null ? null : response.getApplied(),
                    response == null ? null : response.getAppliedCardCount(),
                    response == null ? null : response.getReason());
        } catch (FeignException e) {
            ErrorCode mappedError = cardFeignErrorMapper.map(
                    e,
                    ErrorCode.CARD_PAYMENT_RESULT_INVALID,
                    ErrorCode.CARD_PAYMENT_RESULT_SEND_FAILED);
            log.error("card payment result notify failed. paymentId={}, userId={}, eventType={}, mappedError={}, body={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    CARD_EVENT_CANCELED,
                    mappedError.name(),
                    e.contentUTF8(),
                    e);
        } catch (RuntimeException e) {
            log.error("card payment result notify failed. paymentId={}, userId={}, eventType={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    CARD_EVENT_CANCELED,
                    e);
        }
    }

    private void logCardPaymentResultRequest(PaymentResultRequest request) {
        try {
            log.info("card payment result request:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
        } catch (JsonProcessingException e) {
            log.warn("card payment result request logging failed. paymentId={}, eventType={}",
                    request == null ? null : request.getPaymentId(),
                    request == null ? null : request.getEventType(),
                    e);
        }
    }

    private PaymentResultRequest.Card toCanceledPaymentResultCard(CardDetailEntity cardDetail) {
        return PaymentResultRequest.Card.builder()
                .paymentCardId(cardDetail.getPayment_card_id())
                .cardId(cardDetail.getCard_id())
                .approvedAmount(cardDetail.getPaid_amount())
                .approvedAt(cardDetail.getPaid_at())
                .build();
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

    private PaymentListResonse.PaymentItem toPaymentItem(CoreEntity payment) {
        return PaymentListResonse.PaymentItem.builder()
                .paymentId(payment.getPaymentId())
                .paymentType(payment.getPayment_type().name())
                .strategyType(payment.getStrategy_type() == null ? null : payment.getStrategy_type().name())
                .status(payment.getPayment_status().name())
                .amount(payment.getAmount())
                .orderName(payment.getOrder_name())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private PaymentAllFetchResponse.MerchantUsage toMerchantUsage(
            CoreRepository.MerchantUsageProjection projection) {
        return PaymentAllFetchResponse.MerchantUsage.builder()
                .merchantName(projection.getMerchantName())
                .paymentCount(nullToZero(projection.getPaymentCount()))
                .paidAmount(nullToZero(projection.getPaidAmount()))
                .build();
    }

    private PaymentAllFetchResponse.CardUsage toCardUsage(CardDetailRepository.CardUsageProjection projection) {
        return PaymentAllFetchResponse.CardUsage.builder()
                .cardId(projection.getCardId())
                .paymentCount(nullToZero(projection.getPaymentCount()))
                .paidAmount(nullToZero(projection.getPaidAmount()))
                .build();
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private PaymentDetailResponse toPaymentDetailResponse(CoreEntity payment, List<CardDetailEntity> cardDetails) {
        return PaymentDetailResponse.builder()
                .userId(payment.getUserId())
                .paymentId(payment.getPaymentId())
                .paymentType(payment.getPayment_type().name())
                .strategyType(payment.getStrategy_type() == null ? null : payment.getStrategy_type().name())
                .status(payment.getPayment_status().name())
                .amount(payment.getAmount())
                .orderName(payment.getOrder_name())
                .orderNo(payment.getOrder_no())
                .paidAt(payment.getPaidAt() == null ? payment.getUpdatedAt() : payment.getPaidAt())
                .canceledAt(payment.getCanceledAt())
                .cards(cardDetails.stream()
                        .map(this::toPaymentCardItem)
                        .toList())
                .build();
    }

    private PaymentDetailResponse.CardItem toPaymentCardItem(CardDetailEntity cardDetail) {
        return PaymentDetailResponse.CardItem.builder()
                .paymentCardId(cardDetail.getPayment_card_id())
                .cardId(cardDetail.getCard_id())
                .cardName(cardDetail.getCard_name())
                .maskedNumber(cardDetail.getMasked_number())
                .paidAmount(cardDetail.getPaid_amount())
                .discountAmount(cardDetail.getDiscount_amount())
                .benefitDesc(cardDetail.getBenefit_desc())
                .cardStatus(cardDetail.getCard_status() == null ? null : cardDetail.getCard_status().name())
                .canceledAt(cardDetail.getCanceled_at())
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
                .remoteRequestId(payment.getRemote_request_id())
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
            Optional<ResponseEntity<PrepareResponse>> replayed = findReplayedPrepareResponse(userId,
                    normalizedIdempotencyKey);
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
                .updatedAt(now)
                .created_at(now)
                .build();
    }

    private DutchPaymentSaveOutcome saveRemoteDeputyPaymentWithIdempotencyGuard(
            Long userId,
            String normalizedIdempotencyKey,
            RemoteMemberPrepareRequest request,
            LocalDateTime now) {
        try {
            CoreEntity payment = coreRepository.saveAndFlush(createRemoteDeputyPaymentEntity(
                    userId,
                    normalizedIdempotencyKey,
                    request,
                    now));
            return DutchPaymentSaveOutcome.saved(payment);
        } catch (DataIntegrityViolationException e) {
            Optional<ResponseEntity<PrepareResponse>> replayed = findReplayedPrepareResponse(userId,
                    normalizedIdempotencyKey);
            if (replayed.isPresent()) {
                return DutchPaymentSaveOutcome.replayed(replayed.get());
            }
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
    }

    private CoreEntity createRemoteDeputyPaymentEntity(
            Long userId,
            String normalizedIdempotencyKey,
            RemoteMemberPrepareRequest request,
            LocalDateTime now) {
        return CoreEntity.builder()
                .userId(userId)
                .merchant_id(request.getMerchantId())
                .idempotencyKey(normalizedIdempotencyKey)
                .order_no(qrService.generateUniqueOrderNo(now))
                .order_name(request.getOrderName())
                .amount(request.getAmount())
                .payment_status(CoreEntity.PaymentStatus.CREATED)
                .payment_type(CoreEntity.PaymentType.REMOTE)
                .channel_type(CoreEntity.ChannelType.ONLINE)
                .remote_request_id(request.getRemoteRequestId())
                .updatedAt(now)
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
                            .mccCode("5814")
                            .amount(payment.getAmount())
                            .build());

            if (recommendList == null || recommendList.getResults() == null) {
                log.warn("recommend response is empty. paymentId={}, userId={}", payment.getPaymentId(), userId);
                pushRecommendFailedEvent(payment.getPaymentId(), ErrorCode.REC_RESPONSE_INVALID);
                return;
            }
            log.info("recommend list response:\n{}",
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(recommendList));
            cacheRecommendation(payment.getPaymentId(), recommendList);

            coreSseService.publishPaymentUpdated(
                    payment.getPaymentId(),
                    CoreSseEventType.RECOMMENDATION_SUCCEEDED,
                    recommendList);

        } catch (FeignException e) {
            ErrorCode mappedError = recommendFeignErrorMapper.map(e);
            log.error("recommend feign error. paymentId={}, userId={}, status={}, mappedError={}, body={}",
                    payment.getPaymentId(), userId, e.status(), mappedError.name(), e.contentUTF8(), e);
            pushRecommendFailedEvent(payment.getPaymentId(), mappedError);
        } catch (CustomException e) {
            log.error("recommend request custom error. paymentId={}, userId={}, errorCode={}",
                    payment.getPaymentId(), userId, e.getErrorCode().name(), e);
            pushRecommendFailedEvent(payment.getPaymentId(), e.getErrorCode());
        } catch (Exception e) {
            log.error("recommend request unexpected error. paymentId={}, userId={}",
                    payment.getPaymentId(), userId, e);
            pushRecommendFailedEvent(payment.getPaymentId(), ErrorCode.REC_INTERNAL_SERVER_ERROR);
        }
    }

    private void cacheRecommendation(Long paymentId, RecommendResponse recommendList) {
        if (paymentId == null || recommendList == null || recommendList.getResults() == null) {
            return;
        }

        String cacheKey = RECOMMENDATION_CACHE_KEY_PREFIX + paymentId;
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(recommendList),
                    RECOMMENDATION_CACHE_TTL);
            log.info("recommendation cache saved. key={}, ttlSeconds={}",
                    cacheKey,
                    RECOMMENDATION_CACHE_TTL.toSeconds());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("recommendation cache save failed. paymentId={}", paymentId, e);
            throw new CustomException(ErrorCode.REC_CACHE_WRITE_FAILED, e);
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

    private void pushRecommendFailedEvent(Long paymentId, ErrorCode errorCode) {
        pushRecommendFailedEvent(paymentId, errorCode.getStatus().value(), errorCode.getMessage());
    }

    @FunctionalInterface
    private interface DutchPaymentPostProcessor {
        void process(CoreEntity payment);
    }

    private record PaymentHistoryDateRange(LocalDateTime from, LocalDateTime to) {
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
