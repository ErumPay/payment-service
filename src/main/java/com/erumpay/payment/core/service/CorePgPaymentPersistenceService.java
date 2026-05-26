package com.erumpay.payment.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CorePgPaymentPersistenceService {

    private static final String BILLING_KEY_LOOKUP_FAILED = "BILLING_KEY_LOOKUP_FAILED";

    private final CoreRepository coreRepository;
    private final EventRepository eventRepository;

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
                .actor_type(EventEntity.ActorType.SYSTEM)
                .created_at(pgResponse != null && pgResponse.getProcessedAt() != null
                        ? pgResponse.getProcessedAt()
                        : LocalDate.now())
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
}
