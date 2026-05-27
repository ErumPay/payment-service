package com.erumpay.payment.core.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.erumpay.payment.core.client.pg.PgClient;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayRequest;
import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.PinAndPayRequest;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorePgPaymentService {

    private static final String AUTHORIZATION = "Bearer server-test-token";
    private static final String PG_STATUS_APPROVED = "APPROVED";
    private static final String PG_STATUS_REJECTED = "REJECTED";

    private final PgClient pgClient;
    private final EventRepository eventRepository;
    private final CorePgPaymentPersistenceService corePgPaymentPersistenceService;

    // [be] 다윤 260526 pg-payment-service 실결제 요청
    public void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        boolean isDutchHost = payment.getPayment_type() == CoreEntity.PaymentType.DUTCH
                && payment.getDutch_role() == CoreEntity.DutchRole.HOST;

        // [be] 다윤 260527 단일 카드 결제 요청만 강제
        for (PinAndPayRequest.CardPortion card : request.getCards()) {
            PgAuthPayRequest pgAuthRequest = PgAuthPayRequest.builder()
                    .payPaymentId(payment.getPaymentId())
                    .merchantId(payment.getMerchant_id())
                    .billingKey(String.valueOf(card.getCardId()))
                    .originalAmount(payment.getAmount())
                    .approvedAmount(card.getAmount())
                    .build();

            final PgAuthPayResponse pgResponse;
            try {
                pgResponse = isDutchHost
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
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.BAD_REQUEST);
                }
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            log.info("pgClientResponse : {}", pgResponse);

            if (pgResponse == null || pgResponse.getStatus() == null) {
                corePgPaymentPersistenceService.markFailedAndSaveEvent(payment.getPaymentId(), pgResponse);
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }

            String pgStatus = pgResponse.getStatus();
            if (PG_STATUS_APPROVED.equals(pgStatus)) {
                payment.paidStatusUpdatePayment(LocalDateTime.now());
                saveEvent(payment, pgResponse, EventEntity.EventType.PAID);
                continue;
            }

            corePgPaymentPersistenceService.markFailedAndSaveEvent(payment.getPaymentId(), pgResponse);

            if (PG_STATUS_REJECTED.equals(pgStatus)) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void saveEvent(CoreEntity payment, PgAuthPayResponse pgResponse, EventEntity.EventType eventType) {
        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .event_type(eventType)
                .actor_type(EventEntity.ActorType.SYSTEM)
                .created_at(pgResponse != null && pgResponse.getProcessedAt() != null ? pgResponse.getProcessedAt()
                        : LocalDateTime.now())
                .build();

        eventRepository.save(savedEvent);
    }
}
