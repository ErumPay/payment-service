package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.client.card.CardClient;
import com.erumpay.payment.core.client.card.dto.CardBillingKeyResponse;
import com.erumpay.payment.core.client.card.dto.CardBillingKeysRequest;
import com.erumpay.payment.core.client.card.dto.CardBillingKeysResponse;
import com.erumpay.payment.core.client.card.dto.PaymentResultRequest;
import com.erumpay.payment.core.client.card.dto.PaymentResultResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.PaidCardRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostFinalPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.service.DutchPayService;
import com.erumpay.payment.remote.service.RemotePayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorePgPaymentService {

    private final CoreSseService coreSseService;
    private static final String PG_STATUS_APPROVED = "APPROVED";
    private static final String PG_STATUS_REJECTED = "REJECTED";
    private static final String PG_STATUS_FAILED = "FAILED";
    private static final String PG_STATUS_VOIDED = "VOIDED";
    private static final String CARD_EVENT_APPROVED = "APPROVED";
    private static final String HOST_AUTH_STATUS_AUTHORIZED = "AUTHORIZED";
    private static final String HOST_AUTH_STATUS_FAILED = "FAILED";
    private static final String PARTICIPANT_PAYMENT_STATUS_PAID = "PAID";
    private static final String RECOMMENDATION_CACHE_KEY_PREFIX = "payment:recommendation:";

    private final PgClient pgClient;
    private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;
    private final DutchPayService dutchPayService;
    private final RemotePayService remotePayService;
    private final CardClient cardClient;
    private final EventRepository eventRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pg.authorization}")
    private String pgAuthorization;

    // [be] 다윤 260601 20:00 | pg 에게 결제 요청 진입점
    public void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        boolean useAuthOnly = shouldUseAuthOnly(payment);

        RecommendResponse.Result selectedRecommendation = validateRecommendationSelection(payment.getPaymentId(),
                request);

        Map<Long, CardBillingKeyResponse> billingKeys = fetchBillingKeysOrThrow(payment, request.getCards());

        publishPendingEvent(payment.getPaymentId());

        List<ApprovedCardPayment> approvedPayments = requestApprovedCardPayments(
                payment,
                request,
                billingKeys,
                savedIdempotencyKey,
                useAuthOnly);

        if (useAuthOnly) {
            PgAuthPayResponse pgResponse = approvedPayments.get(0).pgResponse();
            corePgPaymentPersistenceService.markAuthorizedAndSaveEvent(payment.getPaymentId(), pgResponse);
            notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_AUTHORIZED, pgResponse);
            publishAuthorizedEvent(payment.getPaymentId());
            return;
        }

        markPaymentSucceeded(payment, approvedPayments, selectedRecommendation);
        publishPaidEvent(payment.getPaymentId());
    }

    private List<ApprovedCardPayment> requestApprovedCardPayments(
            CoreEntity payment,
            PinAndPayRequest request,
            Map<Long, CardBillingKeyResponse> billingKeys,
            String savedIdempotencyKey,
            boolean useAuthOnly) {
        if (useAuthOnly && request.getCards().size() != 1) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        List<ApprovedCardPayment> approvedPayments = new ArrayList<>();

        for (int i = 0; i < request.getCards().size(); i++) {
            PinAndPayRequest.CardPortion card = request.getCards().get(i);
            CardBillingKeyResponse billingKey = findBillingKeyOrThrow(payment, card, billingKeys);
            String pgIdempotencyKey = resolvePgIdempotencyKey(savedIdempotencyKey, card, i, useAuthOnly);
            PgAuthPayResponse pgResponse;
            try {
                pgResponse = requestPgPayment(payment, card, billingKey, pgIdempotencyKey, useAuthOnly);
            } catch (RuntimeException e) {
                failPaymentAfterPgFailure(payment, null, approvedPayments, savedIdempotencyKey, e);
                throw e;
            }

            if (pgResponse == null || pgResponse.getStatus() == null) {
                failPaymentAfterPgFailure(
                        payment,
                        pgResponse,
                        approvedPayments,
                        savedIdempotencyKey,
                        new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR));
            }

            String pgStatus = pgResponse.getStatus();
            if (PG_STATUS_APPROVED.equalsIgnoreCase(pgStatus)) {
                approvedPayments.add(new ApprovedCardPayment(card, billingKey, pgResponse));
                continue;
            }

            ErrorCode errorCode = resolvePgFailureErrorCode(pgStatus);
            failPaymentAfterPgFailure(
                    payment,
                    pgResponse,
                    approvedPayments,
                    savedIdempotencyKey,
                    new CustomException(errorCode));
        }

        return approvedPayments;
    }

    private ErrorCode resolvePgFailureErrorCode(String pgStatus) {
        if (PG_STATUS_REJECTED.equalsIgnoreCase(pgStatus)) {
            return ErrorCode.PG_PAYMENT_REJECTED;
        }
        if (PG_STATUS_FAILED.equalsIgnoreCase(pgStatus)) {
            return ErrorCode.PG_PAYMENT_FAILED;
        }
        return ErrorCode.PG_PAYMENT_STATUS_INVALID;
    }

    private String resolvePgIdempotencyKey(
            String savedIdempotencyKey,
            PinAndPayRequest.CardPortion card,
            int index,
            boolean useAuthOnly) {
        if (useAuthOnly) {
            return savedIdempotencyKey;
        }
        return savedIdempotencyKey + "-card-" + (index + 1) + "-" + card.getCardId();
    }

    private void failPaymentAfterPgFailure(
            CoreEntity payment,
            PgAuthPayResponse pgResponse,
            List<ApprovedCardPayment> approvedPayments,
            String savedIdempotencyKey,
            RuntimeException exception) {
        compensateApprovedPayments(payment, approvedPayments, savedIdempotencyKey);
        markPaymentFailed(payment, pgResponse);
        publishFailedEvent(payment.getPaymentId());
        throw exception;
    }

    private void compensateApprovedPayments(
            CoreEntity payment,
            List<ApprovedCardPayment> approvedPayments,
            String savedIdempotencyKey) {
        for (ApprovedCardPayment approvedPayment : approvedPayments) {
            Long pgTxnId = approvedPayment.pgResponse().getPgTxnId();
            if (pgTxnId == null) {
                continue;
            }

            PgPayCancelRequest cancelRequest = PgPayCancelRequest.builder()
                    .payPaymentId(payment.getPaymentId())
                    .merchantId(payment.getMerchant_id())
                    .cancelReason("MULTI_CARD_PAYMENT_FAILED")
                    .build();
            String compensationIdempotencyKey = savedIdempotencyKey + "-compensate-" + pgTxnId;

            try {
                PgAuthPayResponse cancelResponse = pgClient.pgPaymentCancelRequest(
                        pgAuthorization,
                        compensationIdempotencyKey,
                        pgTxnId,
                        cancelRequest);
                log.info("pg compensation cancel requested. paymentId={}, pgTxnId={}, status={}",
                        payment.getPaymentId(),
                        pgTxnId,
                        cancelResponse == null ? null : cancelResponse.getStatus());
            } catch (RuntimeException e) {
                log.error("pg compensation cancel failed. paymentId={}, pgTxnId={}",
                        payment.getPaymentId(),
                        pgTxnId,
                        e);
            }
        }
    }

    // [be] 다윤 260603 21:00 | redis 카드 조합과 요청 카드 조합 일치 여부 검증
    private RecommendResponse.Result validateRecommendationSelection(Long paymentId, PinAndPayRequest request) {
        RecommendResponse recommendResponse = loadCachedRecommendation(paymentId);
        RecommendResponse.Result selectedResult = findSelectedRecommendation(recommendResponse,
                request.getStrategyType());

        if (selectedResult == null || selectedResult.getCards() == null
                || selectedResult.getCards().size() != request.getCards().size()) {
            throw new CustomException(ErrorCode.RECOMMENDATION_SELECTION_INVALID);
        }

        for (PinAndPayRequest.CardPortion requestedCard : request.getCards()) {
            if (findMatchingRecommendedCard(selectedResult.getCards(), requestedCard) == null) {
                throw new CustomException(ErrorCode.RECOMMENDATION_SELECTION_INVALID);
            }
        }

        log.info("recommendation selection validated. paymentId={}, strategyType={}",
                paymentId,
                request.getStrategyType());
        return selectedResult;
    }

    // [be] 다윤 260603 21:00 | redis에 캐시된 추천 응답 조회
    private RecommendResponse loadCachedRecommendation(Long paymentId) {
        String cacheKey = RECOMMENDATION_CACHE_KEY_PREFIX + paymentId;
        try {
            String cachedRecommendation = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedRecommendation == null || cachedRecommendation.isBlank()) {
                log.warn("recommendation cache missing. paymentId={}, key={}", paymentId, cacheKey);
                throw new CustomException(ErrorCode.RECOMMENDATION_SELECTION_INVALID);
            }

            return objectMapper.readValue(cachedRecommendation, RecommendResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("recommendation cache read failed. paymentId={}, key={}", paymentId, cacheKey, e);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    // [be] 다윤 260603 21:00 | 요청 strategyType에 해당하는 추천 조합 조회
    private RecommendResponse.Result findSelectedRecommendation(RecommendResponse recommendResponse,
            String strategyType) {
        if (recommendResponse == null || recommendResponse.getResults() == null || strategyType == null) {
            return null;
        }

        return recommendResponse.getResults().stream()
                .filter(result -> result != null && strategyType.equals(result.getStrategyType()))
                .findFirst()
                .orElse(null);
    }

    // [be] 다윤 260603 21:00 | 선택된 결제 카드 조합의 카드와 금액 일치 여부 확인
    private RecommendResponse.Card findMatchingRecommendedCard(
            List<RecommendResponse.Card> recommendedCards,
            PinAndPayRequest.CardPortion requestedCard) {
        if (requestedCard == null) {
            return null;
        }

        return recommendedCards.stream()
                .filter(recommendedCard -> recommendedCard != null
                        && requestedCard.getCardId().equals(recommendedCard.getCardId())
                        && requestedCard.getAmount().equals(recommendedCard.getAmount()))
                .findFirst()
                .orElse(null);
    }

    // [be] 다윤 260601 20:00 | pg 에게 AUTH 또는 AUTH-ONLY 요청 분기
    private PgAuthPayResponse requestPgPayment(
            CoreEntity payment,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey,
            String savedIdempotencyKey,
            boolean useAuthOnly) {
        PgAuthPayRequest pgAuthRequest = buildPgAuthRequest(payment, card, billingKey);

        try {
            PgAuthPayResponse pgResponse = useAuthOnly
                    ? pgClient.pgPaymentAuthOnlyRequest(
                            pgAuthorization,
                            savedIdempotencyKey,
                            pgAuthRequest)
                    : pgClient.pgPaymentRequest(
                            pgAuthorization,
                            savedIdempotencyKey,
                            pgAuthRequest);
            log.info(
                    "pg payment success. paymentId={}, cardId={}, status={}, pgTxnId={}, txnType={}",
                    payment.getPaymentId(),
                    card.getCardId(),
                    pgResponse == null ? null : pgResponse.getStatus(),
                    pgResponse == null ? null : pgResponse.getPgTxnId(),
                    pgResponse == null ? null : pgResponse.getTxnType());
            return pgResponse;
        } catch (FeignException e) {
            log.error("pg feign error. status={}, body={}", e.status(), e.contentUTF8());
            if (e.status() >= 400 && e.status() < 500) {
                throw new CustomException(ErrorCode.PG_PAYMENT_REJECTED, e);
            }
            throw new CustomException(ErrorCode.PG_PAYMENT_FAILED, e);
        }
    }

    // [be] 다윤 260601 20:00 | pg request dto
    private PgAuthPayRequest buildPgAuthRequest(
            CoreEntity payment,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey) {
        return PgAuthPayRequest.builder()
                .payPaymentId(payment.getPaymentId())
                .merchantId(payment.getMerchant_id())
                .billingKey(billingKey.getBillingKey())
                .originalAmount(payment.getAmount())
                .approvedAmount(card.getAmount())
                .build();
    }

    // [be] 다윤 260601 20:00 | 결제 성공 시 원장기록, 가승인의 경우 더치에게 가승인 성공 전달, 결제카드 기록
    private void markPaymentSucceeded(
            CoreEntity payment,
            List<ApprovedCardPayment> approvedPayments,
            RecommendResponse.Result selectedRecommendation) {
        if (approvedPayments.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        PgAuthPayResponse firstPgResponse = approvedPayments.get(0).pgResponse();
        corePgPaymentPersistenceService.markPaidAndSaveEvent(
                payment.getPaymentId(),
                firstPgResponse,
                selectedRecommendation.getStrategyType());

        List<CardDetailEntity> savedCardDetails = new ArrayList<>();
        try {
            for (ApprovedCardPayment approvedPayment : approvedPayments) {
                PaidCardRequest paidCard = buildPaidCardRequest(
                        payment,
                        approvedPayment.pgResponse(),
                        approvedPayment.card(),
                        approvedPayment.billingKey(),
                        selectedRecommendation);
                savedCardDetails.add(corePgPaymentPersistenceService.savePaidCardDetail(paidCard));
            }
        } catch (RuntimeException e) {
            log.error(
                    "paid card detail save failed, but payment is already marked PAID. paymentId={}",
                    payment.getPaymentId(),
                    e);
        }
        notifyCardPaymentApproved(
                payment,
                savedCardDetails,
                approvedPayments,
                selectedRecommendation,
                firstPgResponse);
        notifyParticipantPaymentResultIfNeeded(payment, PARTICIPANT_PAYMENT_STATUS_PAID, firstPgResponse);
        notifyRemotePaymentResultIfNeeded(payment);
        notifyHostFinalPaymentResultIfNeeded(payment, PARTICIPANT_PAYMENT_STATUS_PAID, firstPgResponse);
    }

    // [be] 다윤 260601 20:00 | 결제 실패 시 원장기록, 가승인의 경우 더치에게 가승인 실패 전달
    private void markPaymentFailed(CoreEntity payment, PgAuthPayResponse pgResponse) {
        corePgPaymentPersistenceService.markFailedAndSaveEvent(payment.getPaymentId(), pgResponse);
        notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_FAILED, pgResponse);
    }

    // [be] 다윤 260601 20:00 | SSE PG_PENDING push
    private void publishPendingEvent(Long paymentId) {
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.PG_PENDING,
                Map.of("status", "PG_PENDING"));
    }

    // [be] 다윤 260601 20:00 | SSE PAID push
    private void publishPaidEvent(Long paymentId) {
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.PAYMENT_PAID,
                Map.of("status", "PAID"));
    }

    // [be] 다윤 260604 | SSE AUTHORIZED push
    private void publishAuthorizedEvent(Long paymentId) {
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.PAYMENT_AUTHORIZED,
                Map.of("status", "AUTHORIZED"));
    }

    // [be] 다윤 260601 20:00 | SSE FAILED push
    private void publishFailedEvent(Long paymentId) {
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.PAYMENT_FAILED,
                Map.of("status", "FAILED"));
    }

    // [be] 다윤 260601 20:00 | 결제할 카드의 빌링키 조회 요청 - card service feign 통신
    private Map<Long, CardBillingKeyResponse> fetchBillingKeysOrThrow(
            CoreEntity payment,
            List<PinAndPayRequest.CardPortion> cards) {
        List<Long> cardIds = cards.stream()
                .map(PinAndPayRequest.CardPortion::getCardId)
                .toList();

        try {
            CardBillingKeysResponse response = cardClient.billingKeysLookUp(
                    payment.getUserId(),
                    CardBillingKeysRequest.builder()
                            .cardIds(cardIds)
                            .build());
            log.info(
                    "card billing-keys lookup success. paymentId={}, cardIds={}, userId={}, count={}",
                    payment.getPaymentId(),
                    cardIds,
                    payment.getUserId(),
                    response == null || response.getBillingKeys() == null ? 0 : response.getBillingKeys().size());

            if (response == null || response.getBillingKeys() == null
                    || response.getBillingKeys().size() != cardIds.size()) {
                log.error("card billing-keys response invalid. paymentId={}, cardIds={}, userId={}",
                        payment.getPaymentId(), cardIds, payment.getUserId());
                throw new CustomException(ErrorCode.CARD_BILLING_KEY_INVALID);
            }

            return response.getBillingKeys().stream()
                    .collect(Collectors.toMap(CardBillingKeyResponse::getCardId, Function.identity()));
        } catch (FeignException e) {
            ErrorCode mappedError = mapCardBillingKeyError(e.status());
            log.error(
                    "card billing-keys feign error. paymentId={}, cardIds={}, userId={}, status={}, mappedError={}, body={}",
                    payment.getPaymentId(),
                    cardIds,
                    payment.getUserId(),
                    e.status(),
                    mappedError.name(),
                    trimForLog(e.contentUTF8()));

            throw new CustomException(mappedError, e);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("card billing-keys unexpected error. paymentId={}, cardIds={}, userId={}",
                    payment.getPaymentId(), cardIds, payment.getUserId(), e);

            throw new CustomException(ErrorCode.INTERNAL_CARD_SERVER_ERROR, e);
        }
    }

    private CardBillingKeyResponse findBillingKeyOrThrow(
            CoreEntity payment,
            PinAndPayRequest.CardPortion card,
            Map<Long, CardBillingKeyResponse> billingKeys) {
        CardBillingKeyResponse billingKey = billingKeys.get(card.getCardId());
        if (billingKey == null || billingKey.getBillingKey() == null || billingKey.getBillingKey().isBlank()) {
            log.error("card billing-key is empty. paymentId={}, cardId={}, userId={}",
                    payment.getPaymentId(), card.getCardId(), payment.getUserId());
            throw new CustomException(ErrorCode.CARD_BILLING_KEY_INVALID);
        }
        return billingKey;
    }

    private ErrorCode mapCardBillingKeyError(int status) {
        return switch (status) {
            case 400, 422 -> ErrorCode.CARD_BILLING_KEY_INVALID;
            case 401, 403 -> ErrorCode.CARD_BILLING_KEY_FORBIDDEN;
            case 404 -> ErrorCode.CARD_BILLING_KEY_NOT_FOUND;
            default -> ErrorCode.INTERNAL_CARD_SERVER_ERROR;
        };
    }

    private String trimForLog(String body) {
        if (body == null) {
            return "";
        }

        int maxLen = 500;
        return body.length() <= maxLen ? body : body.substring(0, maxLen) + "...";
    }

    private PaidCardRequest buildPaidCardRequest(
            CoreEntity payment,
            PgAuthPayResponse pgResponse,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey,
            RecommendResponse.Result selectedRecommendation) {
        if (payment == null
                || payment.getPaymentId() == null
                || pgResponse == null
                || pgResponse.getPgTxnId() == null
                || card == null
                || card.getCardId() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        String approvalNumber = pgResponse.getPgApprovalNumber();
        if (approvalNumber == null || approvalNumber.isBlank()) {
            approvalNumber = "UNKNOWN_APPROVAL";
        }

        Long paidAmount = pgResponse.getAmount();
        if (paidAmount == null) {
            paidAmount = card.getAmount();
        }
        if (paidAmount == null) {
            log.warn("paid amount is missing. fallback to 0. paymentId={}, pgTxnId={}, cardId={}",
                    payment.getPaymentId(),
                    pgResponse.getPgTxnId(),
                    card.getCardId());
            paidAmount = 0L;
        }

        LocalDateTime paidAt = pgResponse.getApprovedAt() == null ? LocalDateTime.now() : pgResponse.getApprovedAt();
        String maskedNumber = (billingKey == null || billingKey.getMaskedNumber() == null
                || billingKey.getMaskedNumber().isBlank())
                        ? "UNKNOWN_MASKED"
                        : billingKey.getMaskedNumber();
        String cardName = (billingKey == null || billingKey.getCardName() == null || billingKey.getCardName().isBlank())
                ? "UNKNOWN_CARD"
                : billingKey.getCardName();
        RecommendResponse.Card recommendedCard = selectedRecommendation == null ? null
                : findMatchingRecommendedCard(selectedRecommendation.getCards(), card);
        Long discountAmount = recommendedCard == null || recommendedCard.getDiscountAmount() == null
                ? 0L
                : recommendedCard.getDiscountAmount();
        String benefitDesc = selectedRecommendation == null ? null : selectedRecommendation.getReason();

        return PaidCardRequest.builder()
                .paymentId(payment.getPaymentId())
                .pgTxnId(pgResponse.getPgTxnId())
                .pgApprovalNum(approvalNumber)
                .cardId(card.getCardId())
                .maskedNumber(maskedNumber)
                .cardName(cardName)
                .paidAmount(paidAmount)
                .discountAmount(discountAmount)
                .benefitDesc(benefitDesc)
                .paidAt(paidAt)
                .build();
    }

    private void notifyCardPaymentApproved(
            CoreEntity payment,
            List<CardDetailEntity> savedCardDetails,
            List<ApprovedCardPayment> approvedPayments,
            RecommendResponse.Result selectedRecommendation,
            PgAuthPayResponse firstPgResponse) {
        if (payment.getUserId() == null) {
            log.error("card payment result notify skipped. paymentId={}, eventType={}, userId=null",
                    payment.getPaymentId(),
                    CARD_EVENT_APPROVED);
            return;
        }

        try {
            PaymentResultRequest request = PaymentResultRequest.builder()
                    .paymentId(payment.getPaymentId())
                    .eventType(CARD_EVENT_APPROVED)
                    .occurredAt(resolvePaymentResultOccurredAt(firstPgResponse))
                    .cards(buildApprovedPaymentResultCards(
                            savedCardDetails,
                            approvedPayments,
                            selectedRecommendation))
                    .build();

            logCardPaymentResultRequest(request);
            PaymentResultResponse response = cardClient.paymentResultSend(payment.getUserId(), request);
            log.info(
                    "card payment result notify success. paymentId={}, userId={}, eventType={}, applied={}, appliedCardCount={}, reason={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    CARD_EVENT_APPROVED,
                    response == null ? null : response.getApplied(),
                    response == null ? null : response.getAppliedCardCount(),
                    response == null ? null : response.getReason());
        } catch (RuntimeException e) {
            log.error("card payment result notify failed. paymentId={}, userId={}, eventType={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    CARD_EVENT_APPROVED,
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

    private LocalDateTime resolvePaymentResultOccurredAt(PgAuthPayResponse pgResponse) {
        if (pgResponse == null) {
            return LocalDateTime.now();
        }
        if (pgResponse.getApprovedAt() != null) {
            return pgResponse.getApprovedAt();
        }
        if (pgResponse.getProcessedAt() != null) {
            return pgResponse.getProcessedAt();
        }
        return LocalDateTime.now();
    }

    private List<PaymentResultRequest.Card> buildApprovedPaymentResultCards(
            List<CardDetailEntity> savedCardDetails,
            List<ApprovedCardPayment> approvedPayments,
            RecommendResponse.Result selectedRecommendation) {
        if (savedCardDetails != null && savedCardDetails.size() == approvedPayments.size()) {
            return savedCardDetails.stream()
                    .map(this::toPaymentResultCard)
                    .toList();
        }

        return approvedPayments.stream()
                .map(approvedPayment -> toPaymentResultCard(approvedPayment, selectedRecommendation))
                .toList();
    }

    private PaymentResultRequest.Card toPaymentResultCard(CardDetailEntity cardDetail) {
        return PaymentResultRequest.Card.builder()
                .paymentCardId(cardDetail.getPayment_card_id())
                .cardId(cardDetail.getCard_id())
                .approvedAmount(cardDetail.getPaid_amount())
                .approvedAt(cardDetail.getPaid_at())
                .appliedBenefit(toAppliedBenefit(cardDetail.getDiscount_amount()))
                .build();
    }

    private PaymentResultRequest.Card toPaymentResultCard(
            ApprovedCardPayment approvedPayment,
            RecommendResponse.Result selectedRecommendation) {
        PinAndPayRequest.CardPortion card = approvedPayment.card();
        PgAuthPayResponse pgResponse = approvedPayment.pgResponse();
        RecommendResponse.Card recommendedCard = selectedRecommendation == null ? null
                : findMatchingRecommendedCard(selectedRecommendation.getCards(), card);
        Long approvedAmount = pgResponse == null || pgResponse.getAmount() == null
                ? card.getAmount()
                : pgResponse.getAmount();
        Long discountAmount = recommendedCard == null ? null : recommendedCard.getDiscountAmount();

        return PaymentResultRequest.Card.builder()
                .cardId(card.getCardId())
                .approvedAmount(approvedAmount)
                .approvedAt(resolvePaymentResultOccurredAt(pgResponse))
                .appliedBenefit(toAppliedBenefit(discountAmount))
                .build();
    }

    private PaymentResultRequest.AppliedBenefit toAppliedBenefit(Long benefitAmount) {
        if (benefitAmount == null || benefitAmount <= 0) {
            return null;
        }

        return PaymentResultRequest.AppliedBenefit.builder()
                .benefitAmount(benefitAmount)
                .build();
    }

    private record ApprovedCardPayment(
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey,
            PgAuthPayResponse pgResponse) {
    }

    private boolean shouldUseAuthOnly(CoreEntity payment) {
        if (payment.getPayment_type() != CoreEntity.PaymentType.DUTCH) {
            return false;
        }

        CoreEntity.PaymentIntent paymentIntent = payment.getPayment_intent();
        if (paymentIntent == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return paymentIntent == CoreEntity.PaymentIntent.DUTCH_HOST_AUTH_ONLY_PAY;
    }

    // [be] 다윤 260601 20:00 | 더치에게 가승인 여부를 전달
    private void notifyHostAuthorizationResultIfNeeded(
            CoreEntity payment,
            String status,
            PgAuthPayResponse pgResponse) {
        if (!shouldUseAuthOnly(payment)) {
            return;
        }
        if (payment.getDutch_session_id() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        dutchPayService.applyHostAuthorizationResult(
                payment.getDutch_session_id(),
                DutchPayHostAuthorizationResultRequest.builder()
                        .payment_id(payment.getPaymentId())
                        .status(status)
                        .fail_code(pgResponse == null ? null : pgResponse.getFailureCode())
                        .build());
    }

    // [be] 다윤 260601 20:00 | 참여자 결제 완료 여부를 더치에게 전달
    private void notifyParticipantPaymentResultIfNeeded(
            CoreEntity payment,
            String status,
            PgAuthPayResponse pgResponse) {

        log.info("paricipant payment result: {}", status);

        if (!shouldNotifyParticipantPaymentResult(payment)) {
            return;
        }

        if (payment.getDutch_session_id() == null || payment.getUserId() == null) {
            log.error("participant payment result notify skipped. paymentId={}, sessionId={}, userId={}, failCode={}",
                    payment.getPaymentId(),
                    payment.getDutch_session_id(),
                    payment.getUserId(),
                    pgResponse == null ? null : pgResponse.getFailureCode());
            return;
        }

        try {
            dutchPayService.applyParticipantPaymentResult(
                    payment.getDutch_session_id(),
                    DutchPayParticipantPaymentResultRequest.builder()
                            .user_id(payment.getUserId())
                            .payment_id(payment.getPaymentId())
                            .status(status)
                            .build());
        } catch (RuntimeException e) {
            log.error("participant payment result notify failed. paymentId={}, sessionId={}, userId={}, failCode={}",
                    payment.getPaymentId(),
                    payment.getDutch_session_id(),
                    payment.getUserId(),
                    pgResponse == null ? null : pgResponse.getFailureCode(),
                    e);
        }
    }

    // [be] 다윤 260602 | 대표자 최종 결제 완료 여부를 더치에게 전달, pg로 void 요청
    private void notifyHostFinalPaymentResultIfNeeded(
            CoreEntity payment,
            String status,
            PgAuthPayResponse pgResponse) {

        log.info("host final payment result: {}", status);

        if (!shouldNotifyHostFinalPaymentResult(payment)) {
            return;
        }

        if (payment.getDutch_session_id() == null || payment.getUserId() == null) {
            log.error("host final payment result notify skipped. paymentId={}, sessionId={}, userId={}, failCode={}",
                    payment.getPaymentId(),
                    payment.getDutch_session_id(),
                    payment.getUserId(),
                    pgResponse == null ? null : pgResponse.getFailureCode());
            return;
        }

        try {
            DutchPaySessionDetailResponse sessionDetail = dutchPayService.applyHostFinalPaymentResult(
                    payment.getDutch_session_id(),
                    DutchPayHostFinalPaymentResultRequest.builder()
                            .user_id(payment.getUserId())
                            .payment_id(payment.getPaymentId())
                            .status(status)
                            .build());
            voidHostAuthorizationIfNeeded(payment, sessionDetail);
        } catch (RuntimeException e) {
            log.error("host final payment result notify failed. paymentId={}, sessionId={}, userId={}, failCode={}",
                    payment.getPaymentId(),
                    payment.getDutch_session_id(),
                    payment.getUserId(),
                    pgResponse == null ? null : pgResponse.getFailureCode(),
                    e);
        }
    }

    // [be] 다윤 260602 18:00 | pg로 대표자 authorized에 대한 결제 건 void 처리 요청
    private void voidHostAuthorizationIfNeeded(
            CoreEntity finalPayment,
            DutchPaySessionDetailResponse sessionDetail) {
        if (sessionDetail == null || sessionDetail.getHost_auth_payment_id() == null) {
            log.error("host auth void skipped. finalPaymentId={}, sessionId={}, hostAuthPaymentId=null",
                    finalPayment.getPaymentId(),
                    finalPayment.getDutch_session_id());
            return;
        }

        Long hostAuthPaymentId = sessionDetail.getHost_auth_payment_id();
        Long hostAuthPgTxnId = findHostAuthPgTxnId(hostAuthPaymentId);
        PgPayCancelRequest authCancelRequest = PgPayCancelRequest.builder()
                .payPaymentId(hostAuthPaymentId)
                .merchantId(finalPayment.getMerchant_id())
                .voidReason("DUTCHPAY_COMPLETED")
                .build();
        String voidIdempotencyKey = finalPayment.getIdempotencyKey() + "-void-" + hostAuthPaymentId;

        PgAuthPayResponse pgResponse = pgClient.pgPaymentAuthCancelRequest(
                pgAuthorization,
                voidIdempotencyKey,
                hostAuthPgTxnId,
                authCancelRequest);
        validatePgAuthCancelResponse(pgResponse);
        corePgPaymentPersistenceService.markVoidedAndSaveEvent(hostAuthPaymentId, pgResponse);
    }

    private Long findHostAuthPgTxnId(Long hostAuthPaymentId) {
        List<EventEntity> authorizedEvents = eventRepository.findPgTxnEventsByPaymentIdAndEventType(
                hostAuthPaymentId,
                EventEntity.EventType.AUTHORIZED,
                PageRequest.of(0, 1));
        if (authorizedEvents.isEmpty()) {
            throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
        }
        return authorizedEvents.get(0).getPg_txn_id();
    }

    private void validatePgAuthCancelResponse(PgAuthPayResponse pgResponse) {
        if (pgResponse == null || pgResponse.getStatus() == null) {
            throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
        }

        if (!PG_STATUS_VOIDED.equalsIgnoreCase(pgResponse.getStatus())) {
            throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
        }
    }

    // [be] 다윤 260601 20:00 | 더치페이 여부 판단
    private boolean shouldNotifyParticipantPaymentResult(CoreEntity payment) {
        if (payment.getPayment_type() != CoreEntity.PaymentType.DUTCH) {
            return false;
        }
        return payment.getPayment_intent() == CoreEntity.PaymentIntent.DUTCH_MEMBER_PAY;
    }

    private void notifyRemotePaymentResultIfNeeded(CoreEntity payment) {
        if (payment.getPayment_type() != CoreEntity.PaymentType.REMOTE) {
            return;
        }

        try {
            remotePayService.completeByPayment(payment);
        } catch (RuntimeException e) {
            log.error("remote payment result notify failed. paymentId={}, userId={}",
                    payment.getPaymentId(),
                    payment.getUserId(),
                    e);
        }
    }

    private boolean shouldNotifyHostFinalPaymentResult(CoreEntity payment) {
        if (payment.getPayment_type() != CoreEntity.PaymentType.DUTCH) {
            return false;
        }
        return payment.getPayment_intent() == CoreEntity.PaymentIntent.DUTCH_HOST_PAY;
    }
}
