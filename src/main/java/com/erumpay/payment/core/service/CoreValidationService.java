package com.erumpay.payment.core.service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

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

        CoreEntity.PaymentStatus status = existing.get().getPayment_status();
        if (status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED) {
            CoreEntity payment = existing.get();
            return Optional.of(ResponseEntity.ok(PrepareResponse.builder()
                    .paymentId(payment.getPaymentId())
                    .paymentStatus(status.name())
                    .paymentType(payment.getPayment_type() == null ? null : payment.getPayment_type().name())
                    .paymentIntent(payment.getPayment_intent() == null ? null : payment.getPayment_intent().name())
                    .dutchRole(payment.getDutch_role() == null ? null : payment.getDutch_role().name())
                    .dutchSessionId(payment.getDutch_session_id())
                    .amount(payment.getAmount())
                    .build()));
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
        try {
            return CoreEntity.PaymentType.valueOf(paymentType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    public void validateCardAmounts(PinAndPayRequest request) {
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
        if (status == CoreEntity.PaymentStatus.PAID
                || status == CoreEntity.PaymentStatus.AUTHORIZED
                || status == CoreEntity.PaymentStatus.VOIDED
                || status == CoreEntity.PaymentStatus.FAILED
                || status == CoreEntity.PaymentStatus.EXPIRED) {
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
