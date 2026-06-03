package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.client.card.CardClient;
import com.erumpay.payment.core.client.card.dto.CardBillingKeyResponse;
import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.CoreSseEventType;
import com.erumpay.payment.core.domain.dto.PaidCardRequest;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
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
    private static final String PG_STATUS_VOIDED = "VOIDED";
    private static final String HOST_AUTH_STATUS_AUTHORIZED = "AUTHORIZED";
    private static final String HOST_AUTH_STATUS_FAILED = "FAILED";
    private static final String PARTICIPANT_PAYMENT_STATUS_PAID = "PAID";

    private final PgClient pgClient;
    private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;
    private final DutchPayService dutchPayService;
    private final RemotePayService remotePayService;
    private final CardClient cardClient;
    private final EventRepository eventRepository;

    // [be] 다윤 260601 20:00 | pg 에게 결제 요청 진입점
    public void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        boolean useAuthOnly = shouldUseAuthOnly(payment);
        publishPendingEvent(payment.getPaymentId());

        // [be] 다윤 260527 단일 카드 결제 요청만 강제
        for (PinAndPayRequest.CardPortion card : request.getCards()) {
            CardBillingKeyResponse billingKey = fetchBillingKeyOrThrow(payment, card);
            PgAuthPayResponse pgResponse = requestPgPayment(payment, card, billingKey, savedIdempotencyKey,
                    useAuthOnly);
            handlePgResponse(payment, useAuthOnly, pgResponse, card, billingKey);
        }
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
                            AUTHORIZATION,
                            savedIdempotencyKey,
                            pgAuthRequest)
                    : pgClient.pgPaymentRequest(
                            AUTHORIZATION,
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
            notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_FAILED, null);
            if (e.status() >= 400 && e.status() < 500) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
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

    // [be] 다윤 260601 20:00 | pg 응답 분기 처리 후 SSE push
    private void handlePgResponse(
            CoreEntity payment,
            boolean useAuthOnly,
            PgAuthPayResponse pgResponse,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey) {
        log.info("pgClientResponse : {}", pgResponse);

        if (pgResponse == null || pgResponse.getStatus() == null) {
            markPaymentFailed(payment, pgResponse);
            publishFailedEvent(payment.getPaymentId());
            throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
        }

        String pgStatus = pgResponse.getStatus();
        if (PG_STATUS_APPROVED.equalsIgnoreCase(pgStatus)) {
            markPaymentSucceeded(payment, useAuthOnly, pgResponse, card, billingKey);
            publishPaidEvent(payment.getPaymentId());

            return;
        }

        markPaymentFailed(payment, pgResponse);
        if (PG_STATUS_REJECTED.equalsIgnoreCase(pgStatus)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    // [be] 다윤 260601 20:00 | 결제 성공 시 원장기록, 가승인의 경우 더치에게 가승인 성공 전달, 결제카드 기록
    private void markPaymentSucceeded(
            CoreEntity payment,
            boolean useAuthOnly,
            PgAuthPayResponse pgResponse,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey) {
        if (useAuthOnly) {
            corePgPaymentPersistenceService.markAuthorizedAndSaveEvent(payment.getPaymentId(), pgResponse);
            notifyHostAuthorizationResultIfNeeded(payment, HOST_AUTH_STATUS_AUTHORIZED, pgResponse);
            return;
        }

        corePgPaymentPersistenceService.markPaidAndSaveEvent(payment.getPaymentId(), pgResponse);
        try {
            PaidCardRequest paidCard = buildPaidCardRequest(payment, pgResponse, card, billingKey);
            corePgPaymentPersistenceService.savePaidCardDetail(paidCard);
        } catch (RuntimeException e) {
            log.error(
                    "paid card detail save failed, but payment is already marked PAID. paymentId={}, pgTxnId={}, cardId={}",
                    payment.getPaymentId(),
                    pgResponse == null ? null : pgResponse.getPgTxnId(),
                    card == null ? null : card.getCardId(),
                    e);
        }
        notifyParticipantPaymentResultIfNeeded(payment, PARTICIPANT_PAYMENT_STATUS_PAID, pgResponse);
        notifyRemotePaymentResultIfNeeded(payment);
        notifyHostFinalPaymentResultIfNeeded(payment, PARTICIPANT_PAYMENT_STATUS_PAID, pgResponse);
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

    // [be] 다윤 260601 20:00 | SSE FAILED push
    private void publishFailedEvent(Long paymentId) {
        coreSseService.publishPaymentUpdated(
                paymentId,
                CoreSseEventType.PAYMENT_FAILED,
                Map.of("status", "FAILED"));
    }

    // [be] 다윤 260601 20:00 | 결제할 카드의 빌링키 조회 요청 - card service feign 통신
    private CardBillingKeyResponse fetchBillingKeyOrThrow(
            CoreEntity payment,
            PinAndPayRequest.CardPortion card) {
        try {
            CardBillingKeyResponse billingKey = cardClient.billingKeyLookUp(card.getCardId(), payment.getUserId());
            log.info(
                    "card billing-key lookup success. paymentId={}, cardId={}, userId={}, maskedNumber={}, cardName={}",
                    payment.getPaymentId(),
                    card.getCardId(),
                    payment.getUserId(),
                    billingKey == null ? null : billingKey.getMaskedNumber(),
                    billingKey == null ? "null" : billingKey.getCardName());

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

    private PaidCardRequest buildPaidCardRequest(
            CoreEntity payment,
            PgAuthPayResponse pgResponse,
            PinAndPayRequest.CardPortion card,
            CardBillingKeyResponse billingKey) {
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

        return PaidCardRequest.builder()
                .paymentId(payment.getPaymentId())
                .pgTxnId(pgResponse.getPgTxnId())
                .pgApprovalNum(approvalNumber)
                .cardId(card.getCardId())
                .maskedNumber(maskedNumber)
                .cardName(cardName)
                .paidAmount(paidAmount)
                .discountAmount(0L)
                .benefitDesc(null)
                .paidAt(paidAt)
                .build();
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
                AUTHORIZATION,
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
