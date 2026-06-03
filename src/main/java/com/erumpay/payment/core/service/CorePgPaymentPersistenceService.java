package com.erumpay.payment.core.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.dao.CardDetailRepository;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.PaidCardRequest;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CorePgPaymentPersistenceService {

    private static final String BILLING_KEY_LOOKUP_FAILED = "BILLING_KEY_LOOKUP_FAILED";

    private final CardDetailRepository cardDetailRepository;
    private final CoreRepository coreRepository;
    private final EventRepository eventRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStrategyType(Long paymentId, String strategyType) {
        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.updateStrategyType(strategyType, LocalDateTime.now());
    }

    // [be] 다윤 260601 20:00 | 결제 실패 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.failedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .event_type(EventEntity.EventType.FAILED)
                .fail_code(extractFailCode(pgResponse))
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        eventRepository.save(savedEvent);
    }

    // [be] 다윤 260601 20:00 | 결제 성공 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaidAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.paidStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .event_type(EventEntity.EventType.PAID)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        eventRepository.save(savedEvent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void savePaidCardDetail(PaidCardRequest paidCard) {
        if (paidCard == null || paidCard.getPaymentId() == null || paidCard.getPgTxnId() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        if (cardDetailRepository.existsByPaymentIdAndPgTxnId(paidCard.getPaymentId(), paidCard.getPgTxnId())) {
            return;
        }

        cardDetailRepository.save(toCardDetailEntity(paidCard));
    }

    // [be] 다윤 260601 20:00 | 가승인 성공 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAuthorizedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.authorizedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .event_type(EventEntity.EventType.AUTHORIZED)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        eventRepository.save(savedEvent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markVoidedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.authVoidedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .event_type(EventEntity.EventType.VOIDED)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        eventRepository.save(savedEvent);
    }

    private String extractFailCode(PgAuthPayResponse pgResponse) {
        if (pgResponse == null) {
            return null;
        }

        String failureCode = pgResponse.getFailureCode();
        if (BILLING_KEY_LOOKUP_FAILED.equals(failureCode)) {
            return failureCode;
        }
        return null;
    }

    private CardDetailEntity toCardDetailEntity(PaidCardRequest paidCard) {
        if (paidCard == null
                || paidCard.getPaymentId() == null
                || paidCard.getPgTxnId() == null
                || paidCard.getPgApprovalNum() == null
                || paidCard.getCardId() == null
                || paidCard.getMaskedNumber() == null
                || paidCard.getCardName() == null
                || paidCard.getPaidAmount() == null
                || paidCard.getDiscountAmount() == null
                || paidCard.getPaidAt() == null) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return CardDetailEntity.builder()
                .payment_id(paidCard.getPaymentId())
                .pg_txn_id(paidCard.getPgTxnId())
                .pg_approval_num(paidCard.getPgApprovalNum())
                .card_id(paidCard.getCardId())
                .masked_number(paidCard.getMaskedNumber())
                .card_name(paidCard.getCardName())
                .paid_amount(paidCard.getPaidAmount())
                .discount_amount(paidCard.getDiscountAmount())
                .benefit_desc(paidCard.getBenefitDesc())
                .paid_at(paidCard.getPaidAt())
                .build();
    }
}
