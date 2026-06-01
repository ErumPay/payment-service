package com.erumpay.payment.core.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.erumpay.payment.core.client.card.CardClient;
import com.erumpay.payment.core.client.card.dto.CardBillingKeyResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentResultRequest;
import com.erumpay.payment.dutch.service.DutchPayService;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorePgPaymentService {

    private final CoreSseService coreSseService;
    private static final String AUTHORIZATION = "Bearer server-test-token";
    private static final String PG_STATUS_APPROVED = "APPROVED";
    private static final String PG_STATUS_REJECTED = "REJECTED";
    private static final String HOST_AUTH_STATUS_AUTHORIZED = "AUTHORIZED";
    private static final String HOST_AUTH_STATUS_FAILED = "FAILED";
    private static final String PARTICIPANT_PAYMENT_STATUS_PAID = "PAID";

    private final PgClient pgClient;
    private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;
    private final DutchPayService dutchPayService;
    private final CardClient cardClient;

    public void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        boolean useAuthOnly = shouldUseAuthOnly(payment);
        coreSseService.publishPaymentUpdated(
                payment.getPaymentId(),
                CoreSseEventType.PAYMENT_PENDING,
                Map.of("status", "PENDING"));

        // [be] 다윤 260527 단일 카드 결제 요청만 강제
        for (PinAndPayRequest.CardPortion card : request.getCards()) {

            // [be] 다윤 260529 billing-key 조회
            CardBillingKeyResponse billingKey = fetchBillingKeyOrThrow(payment, card);

            PgAuthPayRequest pgAuthRequest = PgAuthPayRequest.builder()
                    .payPaymentId(payment.getPaymentId())
                    .merchantId(payment.getMerchant_id())
                    .billingKey(billingKey.getBillingKey())
                    .originalAmount(payment.getAmount())
                    .approvedAmount(card.getAmount())
                    .build();

            final PgAuthPayResponse pgResponse;
            try {
                pgResponse = useAuthOnly
                        ? pgClient.pgPaymentAuthOnlyRequest(
                                AUTHORIZATION,
                                savedIdempotencyKey,
                                pgAuthRequest)
                        : pgClient.pgPaymentRequest(
                                AUTHORIZATION,
                                savedIdempotencyKey,
                                pgAuthRequest);
            } catch (FeignException e) {
                log.error("pg feign error. status={}, body={}", e.status(), e.contentUTF8());
                notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_FAILED, null);
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.BAD_REQUEST);
                }
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            log.info("pgClientResponse : {}", pgResponse);

            // [be] 다윤 260528 00:00 | pg 응답 분기
            if (pgResponse == null || pgResponse.getStatus() == null) {
                corePgPaymentPersistenceService.markFailedAndSaveEvent(payment.getPaymentId(), pgResponse);
                notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_FAILED, pgResponse);
                coreSseService.publishPaymentUpdated(
                        payment.getPaymentId(),
                        CoreSseEventType.PAYMENT_FAILED,
                        Map.of("status", "FAILED"));
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
            }
            String pgStatus = pgResponse.getStatus();

            if (PG_STATUS_APPROVED.equalsIgnoreCase(pgStatus)) {
                if (useAuthOnly) {
                    corePgPaymentPersistenceService.markAuthorizedAndSaveEvent(payment.getPaymentId(), pgResponse);
                    notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_AUTHORIZED, pgResponse);
                    coreSseService.publishPaymentUpdated(
                            payment.getPaymentId(),
                            CoreSseEventType.PAYMENT_PAID,
                            Map.of("status", "PAID"));
                } else {
                    corePgPaymentPersistenceService.markPaidAndSaveEvent(payment.getPaymentId(), pgResponse);
                    notifyParticipantPaymentResultIfNeeded(payment, PARTICIPANT_PAYMENT_STATUS_PAID, pgResponse);
                    coreSseService.publishPaymentUpdated(
                            payment.getPaymentId(),
                            CoreSseEventType.PAYMENT_PAID,
                            Map.of("status", "PAID"));
                }
                // [be] 다윤 260528 00:00 | cardDetails 추가 예정
                continue;
            }

            corePgPaymentPersistenceService.markFailedAndSaveEvent(payment.getPaymentId(), pgResponse);
            notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_FAILED, pgResponse);

            if (PG_STATUS_REJECTED.equalsIgnoreCase(pgStatus)) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private CardBillingKeyResponse fetchBillingKeyOrThrow(
            CoreEntity payment,
            PinAndPayRequest.CardPortion card) {
        try {
            CardBillingKeyResponse billingKey = cardClient.billingKeyLookUp(card.getCardId(), payment.getUserId());

            if (billingKey == null || billingKey.getBillingKey() == null || billingKey.getBillingKey().isBlank()) {
                log.error("card billing-key is empty. paymentId={}, cardId={}, userId={}",
                        payment.getPaymentId(), card.getCardId(), payment.getUserId());

                throw new CustomException(ErrorCode.CARD_BILLING_KEY_INVALID);
            }
            return billingKey;
        } catch (FeignException e) {
            ErrorCode mappedError = mapCardBillingKeyError(e.status());
            log.error(
                    "card billing-key feign error. paymentId={}, cardId={}, userId={}, status={}, mappedError={}, body={}",
                    payment.getPaymentId(),
                    card.getCardId(),
                    payment.getUserId(),
                    e.status(),
                    mappedError.name(),
                    trimForLog(e.contentUTF8()));

            throw new CustomException(mappedError, e);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("card billing-key unexpected error. paymentId={}, cardId={}, userId={}",
                    payment.getPaymentId(), card.getCardId(), payment.getUserId(), e);

            throw new CustomException(ErrorCode.INTERNAL_CARD_SERVER_ERROR, e);
        }
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

    private boolean shouldNotifyParticipantPaymentResult(CoreEntity payment) {
        if (payment.getPayment_type() != CoreEntity.PaymentType.DUTCH) {
            return false;
        }
        return payment.getPayment_intent() == CoreEntity.PaymentIntent.DUTCH_MEMBER_PAY;
    }
}
