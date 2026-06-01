package com.erumpay.payment.core.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CoreValidationService {

    // private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile(
    // "^pay:(payment|cancel):[1-9]\\d*:[0-9A-HJKMNP-TV-Z]{26}$");

    private final CoreRepository coreRepository;

    // [be] 다윤 260526 멱등성 키 중복 체크 (userId + idempotencyKey)
    public Optional<ResponseEntity<PrepareResponse>> validateIdempotency(Long userId, String idempotencyKey) {

        Optional<CoreEntity> existing = coreRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        CoreEntity payment = existing.get();
        CoreEntity.PaymentStatus status = payment.getPayment_status();
        if (isTerminalStatus(status)) {
            return Optional.of(ResponseEntity.ok(toPrepareResponse(payment, status)));
        }

        throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
    }

    // public String normalizeIdempotencyKey(String idempotencyKey) {
    // if (idempotencyKey == null || idempotencyKey.isBlank()) {
    // throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
    // }

    // String normalized = idempotencyKey.trim();
    // if (!IDEMPOTENCY_KEY_PATTERN.matcher(normalized).matches()) {
    // throw new CustomException(ErrorCode.INVALID_IDEMPOTENCY_KEY);
    // }
    // return normalized;
    // }

    public String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        return idempotencyKey.trim();
    }

    public CoreEntity.PaymentType parsePaymentType(String paymentType) {
        if (paymentType == null || paymentType.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    public void validateCardAmounts(PinAndPayRequest request) {
        if (request == null || request.getCards() == null || request.getCards().isEmpty() || request.getTotalAmount() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

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

    public void validatePrepareStatus(CoreEntity.PaymentStatus status) {
        if (status == CoreEntity.PaymentStatus.CREATED) {
            return;
        }
        if (status == CoreEntity.PaymentStatus.PAY_PENDING || status == CoreEntity.PaymentStatus.PG_PENDING) {
            throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        if (isTerminalStatus(status)) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        throw new CustomException(ErrorCode.BAD_REQUEST);
    }

    public void validateRequestStatus(CoreEntity.PaymentStatus status) {
        if (status == CoreEntity.PaymentStatus.PAY_PENDING) {
            return;
        }
        if (status == CoreEntity.PaymentStatus.PG_PENDING) {
            throw new CustomException(ErrorCode.REQUEST_IN_PROGRESS);
        }
        if (isTerminalStatus(status)) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }
        throw new CustomException(ErrorCode.BAD_REQUEST);
    }

    private PrepareResponse toPrepareResponse(CoreEntity payment, CoreEntity.PaymentStatus status) {
        return PrepareResponse.builder()
                .paymentId(payment.getPaymentId())
                .paymentStatus(status.name())
                .paymentType(payment.getPayment_type() == null ? null : payment.getPayment_type().name())
                .paymentIntent(payment.getPayment_intent() == null ? null : payment.getPayment_intent().name())
                .dutchRole(payment.getDutch_role() == null ? null : payment.getDutch_role().name())
                .dutchSessionId(payment.getDutch_session_id())
                .amount(payment.getAmount())
                .build();
    }

    private boolean isTerminalStatus(CoreEntity.PaymentStatus status) {
        return status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.CANCELED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED;
    }
}
