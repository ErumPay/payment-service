package com.erumpay.payment.merchant.service;

import java.time.LocalDateTime;
import java.util.UUID;

import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgPayCancelRequest;
import com.erumpay.payment.core.dao.CardDetailRepository;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.service.CoreValidationService;
import com.erumpay.payment.merchant.client.MerchantClient;
import com.erumpay.payment.merchant.client.dto.MerchantResponse;
import com.erumpay.payment.merchant.domain.dto.MerchantCancelResponse;
import com.erumpay.payment.merchant.domain.dto.MerchantPaymentRequest;
import com.erumpay.payment.merchant.domain.dto.MerchantPaymentResponse;
import com.erumpay.payment.notification.service.CoreNotificationEventPublisher;
import com.erumpay.payment.qr.dao.QrRepository;
import com.erumpay.payment.qr.domain.entity.QrEntity;
import com.erumpay.payment.qr.service.QrService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MerchantPaymentService {
    private static final String CANCEL_REASON = "MERCHANT_REQUEST";
    private static final String PG_STATUS_REJECTED = "REJECTED";
    private static final String PG_STATUS_CANCELED = "CANCELED";

    private final CoreRepository coreRepository;
    private final CardDetailRepository cardDetailRepository;
    private final QrRepository qrRepository;
    private final QrService qrService;
    private final CoreValidationService coreValidationService;
    private final MerchantClient merchantClient;
    private final PgClient pgClient;
    private final CoreNotificationEventPublisher coreNotificationEventPublisher;

    @Value("${checkout.redirect-base-url}")
    private String checkoutRedirectBaseUrl;

    @Value("${pg.authorization}")
    private String pgAuthorization;

    // [be] 나영은 260529 1638 | SDK 결제 생성 진입점. merchantId + Idempotency-Key 기준으로 중복 주문
    // 생성을 막는다.
    public MerchantPaymentResponse create(
            Long merchantId,
            String idempotencyKey,
            MerchantPaymentRequest request) {

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        return coreRepository.findByMerchantIdAndIdempotencyKey(merchantId, normalizedIdempotencyKey)
                .map(this::toResponse)
                .orElseGet(() -> createNewPayment(merchantId, normalizedIdempotencyKey, request));
    }

    @Transactional(readOnly = true)
    // [be] 나영은 260529 1638 | SDK 결제 조회. merchant 소유권을 먼저 확인한 뒤 QR 토큰 정보를 포함해 반환한다.
    public MerchantPaymentResponse get(Long merchantId, Long paymentId) {
        CoreEntity payment = findMerchantPayment(merchantId, paymentId);
        return toResponse(payment);
    }

    // [be] 나영은 260529 1638 | SDK 결제 취소. CoreService.cancel()의 userId 권한 경계를 건드리지 않기
    // 위해 별도 흐름으로 둔다.
    public MerchantCancelResponse cancel(Long merchantId, String idempotencyKey, Long paymentId) {
        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        CoreEntity payment = findMerchantPayment(merchantId, paymentId);

        /*
         * Merchant cancel is intentionally kept outside CoreService.cancel().
         *
         * CoreService.cancel() is the user-facing cancel flow and its authorization
         * boundary is X-User-Id. Passing null there would create a hidden "skip user
         * auth" mode in the core service. The merchant API has a different auth
         * boundary: Authorization -> merchantId, then paymentId must belong to that
         * merchant. That ownership check already happened in findMerchantPayment().
         */
        if (payment.getPayment_status() == CoreEntity.PaymentStatus.CANCELED
                || payment.getPayment_status() == CoreEntity.PaymentStatus.VOIDED) {
            return MerchantCancelResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .status(payment.getPayment_status().name())
                    .canceledAt(payment.getCanceledAt())
                    .build();
        }

        if (payment.getPayment_status() != CoreEntity.PaymentStatus.PAID) {
            throw new CustomException(ErrorCode.CANCELED_INVALID);
        }

        var cards = cardDetailRepository.findCancelableCardsByPaymentId(paymentId);
        if (cards.isEmpty()) {
            throw new CustomException(ErrorCode.CANCELED_CARD_INVALID);
        }

        for (CardDetailEntity card : cards) {
            PgPayCancelRequest cancelRequest = PgPayCancelRequest.builder()
                    .payPaymentId(paymentId)
                    .merchantId(payment.getMerchant_id())
                    .cancelReason(CANCEL_REASON)
                    .build();

            PgAuthPayResponse pgResponse;
            String cancelIdempotencyKey = normalizedIdempotencyKey + "-" + card.getPg_txn_id();
            try {
                pgResponse = pgClient.pgPaymentCancelRequest(
                        pgAuthorization,
                        cancelIdempotencyKey,
                        card.getPg_txn_id(),
                        cancelRequest);
            } catch (FeignException e) {
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.CANCELED_PG_REJECTED, e);
                }
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR, e);
            }

            if (pgResponse == null || pgResponse.getStatus() == null) {
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
            }

            if (PG_STATUS_REJECTED.equalsIgnoreCase(pgResponse.getStatus())) {
                throw new CustomException(ErrorCode.CANCELED_PG_REJECTED);
            }

            if (!PG_STATUS_CANCELED.equalsIgnoreCase(pgResponse.getStatus())) {
                throw new CustomException(ErrorCode.INTERNAL_PG_SERVER_ERROR);
            }
        }

        LocalDateTime canceledAt = LocalDateTime.now();
        payment.voidedStatusUpdatePayment(canceledAt);
        coreNotificationEventPublisher.publishPaymentCanceled(
                payment.getUserId(),
                payment.getPaymentId(),
                payment.getMerchant_name());
        coreNotificationEventPublisher.publishPaymentSettlementCanceled(
                payment.getMerchant_id(),
                payment.getPaymentId(),
                payment.getAmount());

        return MerchantCancelResponse.builder()
                .paymentId(payment.getPaymentId())
                .status(payment.getPayment_status().name())
                .canceledAt(canceledAt)
                .build();
    }

    private MerchantPaymentResponse createNewPayment(
            Long merchantId,
            String idempotencyKey,
            MerchantPaymentRequest request) {

        // [be] 나영은 260529 1638 | SDK 요청은 결제 주문과 QR 토큰을 함께 만든다. 실제 결제 진행은 기존 QR validate
        // 이후 코어 흐름을 탄다.
        LocalDateTime now = LocalDateTime.now();
        log.info("Calling merchant-service for merchant payment create. merchantId={}", merchantId);
        MerchantResponse merchant;
        try {
            merchant = merchantClient.merchantInfoRequest(merchantId);
        } catch (FeignException e) {
            log.error("merchant-service call failed during merchant payment create. merchantId={}, status={}",
                    merchantId,
                    e.status(),
                    e);
            throw e;
        }
        logMerchantInfoResponse("merchant.payment.create", merchantId, merchant);
        // validateMerchantInfo(merchant, request.getMerchantName());
        CoreEntity payment = CoreEntity.builder()
                .order_no(qrService.generateUniqueOrderNo(now))
                .merchant_name(request.getMerchantName())
                .amount(request.getAmount())
                .merchant_id(merchantId)
                .channel_type(request.getChannel())
                .payment_status(CoreEntity.PaymentStatus.CREATED)
                .idempotencyKey(idempotencyKey)
                .created_at(now)
                .updatedAt(now)
                .build();
        payment.updateMerchantInfo(
                request.getMerchantName(),
                merchant.getBusinessNumber(),
                merchant.getOwnerName(),
                merchant.getContactPhone(),
                merchant.getBusinessAddress(),
                merchant.getMccCode(),
                now);

        CoreEntity savedPayment = coreRepository.saveAndFlush(payment);
        String token = UUID.randomUUID().toString().replace("-", "");
        qrRepository.save(QrEntity.toEntity(savedPayment, token, now, now.plusMinutes(10)));

        return MerchantPaymentResponse.from(savedPayment, checkoutRedirectBaseUrl + token, token);
    }

    // private void validateMerchantInfo(MerchantResponse merchant, String
    // merchantName) {
    // if (merchant == null
    // || merchantName == null
    // || merchantName.isBlank()
    // || merchant.getMccCode() == null
    // || merchant.getMccCode().isBlank()) {
    // throw new CustomException(ErrorCode.MERCHANT_AUTH_UNAVAILABLE);
    // }
    // }

    // [be] 나영은 260529 1638 | 재조회/멱등 응답에서도 결제창 진입 정보가 유지되도록 QR 토큰을 다시 조립한다.
    private MerchantPaymentResponse toResponse(CoreEntity payment) {
        return qrRepository.findByPaymentId(payment.getPaymentId())
                .map(qr -> MerchantPaymentResponse.from(payment, checkoutRedirectBaseUrl + qr.getToken_hash(),
                        qr.getToken_hash()))
                .orElseGet(() -> MerchantPaymentResponse.from(payment, null, null));
    }

    // [be] 나영은 260529 1638 | merchantId 조건을 함께 걸어 다른 가맹점의 paymentId 조회/취소를 막는다.
    private CoreEntity findMerchantPayment(Long merchantId, Long paymentId) {
        return coreRepository.findByPaymentIdAndMerchantId(paymentId, merchantId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));
    }

    private void logMerchantInfoResponse(String flow, Long requestedMerchantId, MerchantResponse merchant) {
        log.info(
                "Merchant info response. flow={}, requestedMerchantId={}, responseMerchantId={}, hasMerchantName={}, hasMccCode={}",
                flow,
                requestedMerchantId,
                merchant == null ? null : merchant.getMerchantId(),
                merchant != null && merchant.getMerchantName() != null && !merchant.getMerchantName().isBlank(),
                merchant != null && merchant.getMccCode() != null && !merchant.getMccCode().isBlank());
    }
}
