package com.erumpay.payment.core.service;

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

    private final PgClient pgClient;
    private final EventRepository eventRepository;

    // [be] 다윤 260526 pg-payment-service 실결제 요청
    public void requestPgPayments(CoreEntity payment, PinAndPayRequest request) {

        String savedIdempotencyKey = payment.getIdempotencyKey();
        if (savedIdempotencyKey == null || savedIdempotencyKey.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        for (PinAndPayRequest.CardPortion card : request.getCards()) {
            PgAuthPayRequest pgAuthRequest = PgAuthPayRequest.builder()
                    .payPaymentId(payment.getPaymentId())
                    .merchantId(payment.getMerchant_id())
                    .billingKey(String.valueOf(card.getCardId()))
                    .originalAmount(payment.getAmount())
                    .approvedAmount(card.getAmount())
                    .build();

            try {
                PgAuthPayResponse pgResponse = pgClient.pgPaymentRequest(
                        AUTHORIZATION,
                        savedIdempotencyKey,
                        pgAuthRequest);

                log.debug("pgClientResponse : {}", pgResponse);

                if (pgResponse == null) {
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }

                // [be] 다윤 260527 단일 카드 결제 요청만 강제
                payment.paidStatusUpdatePayment(LocalDateTime.now());

                EventEntity savedEvent = EventEntity.builder()
                        .payment_id(payment.getPaymentId())
                        .pg_txn_id(pgResponse.getPgTxnId())
                        .event_type(EventEntity.EventType.PAID)
                        .actor_type(EventEntity.ActorType.SYSTEM)
                        .created_at(pgResponse.getProcessedAt())
                        .build();

                eventRepository.save(savedEvent);
            } catch (FeignException e) {
                log.error("pg feign error. status={}, body={}", e.status(), e.contentUTF8());
                if (e.status() >= 400 && e.status() < 500) {
                    throw new CustomException(ErrorCode.BAD_REQUEST);
                }
                throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
            }
        }
    }
}
