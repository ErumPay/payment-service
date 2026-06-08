package com.erumpay.payment.notification.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.notification.dto.PaymentNotificationEventMessage;
import com.erumpay.payment.notification.dto.PaymentNotificationEventMessage.PaymentEventType;
import com.erumpay.payment.notification.dto.PaymentSettlementEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CoreNotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String paymentTopic;
    private final String paymentSettlementTopic;
    private final ObjectMapper objectMapper;

    public CoreNotificationEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.payment-event:payment.event}") String paymentTopic,
            @Value("${app.kafka.topics.payment-settlement-event:payment.settlement.event}") String paymentSettlementTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentTopic = paymentTopic;
        this.paymentSettlementTopic = paymentSettlementTopic;
    }

    // [be] 다윤 260607 03:00 | 결제 완료 시 호출
    public void publishPaymentCompleted(
            Long userId, Long paymentId, String orderName) {
        publishPaymentEvent(
                PaymentEventType.PAYMENT_COMPLETED,
                "결제 완료",
                userId,
                paymentId,
                buildPaymentCompletedContent(orderName));
    }

    // [be] 다윤 260607 03:00 | 결제 취소 시 호출
    public void publishPaymentCanceled(
            Long userId, Long paymentId, String orderName) {
        publishPaymentEvent(
                PaymentEventType.PAYMENT_CANCELED,
                "결제 취소",
                userId,
                paymentId,
                buildPaymentCanceledContent(orderName));
    }

    // [be] 다윤 260607 03:00 | 트랜잭션 이후 발행 제어
    private void publish(PaymentNotificationEventMessage event) {
        if (event == null || event.getUserId() == null) {
            log.warn("Skip Kafka publish because event or userId is null. event={}", event);
            return;
        }

        runAfterCommit(() -> send(event));
    }

    private void publishSettlement(PaymentSettlementEventMessage event) {
        if (event == null || event.getMerchantId() == null) {
            log.warn("Skip settlement Kafka publish because event or merchantId is null. event={}", event);
            return;
        }

        runAfterCommit(() -> sendSettlement(event));
    }

    private void runAfterCommit(Runnable sendTask) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendTask.run();
                }
            });
            return;
        }

        sendTask.run();
    }

    // [be] 다윤 260607 03:00 | Kafka 이벤트 전송
    private void send(PaymentNotificationEventMessage event) {
        String key = event.getUserId().toString();
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(paymentTopic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error(
                                    "Kafka publish failed. topic={}, key={}, eventId={}, paymentId={}",
                                    paymentTopic,
                                    key,
                                    event.getEventId(),
                                    event.getPaymentId(),
                                    ex);
                            return;
                        }

                        log.info(
                                "Kafka publish success. topic={}, key={}, partition={}, offset={}, eventId={}",
                                result.getRecordMetadata().topic(),
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                event.getEventId());
                    });
        } catch (JsonProcessingException e) {
            log.error(
                    "Kafka publish serialization failed. topic={}, key={}, eventId={}, paymentId={}",
                    paymentTopic,
                    key,
                    event.getEventId(),
                    event.getPaymentId(),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "Kafka publish invocation failed. topic={}, key={}, eventId={}, paymentId={}",
                    paymentTopic,
                    key,
                    event.getEventId(),
                    event.getPaymentId(),
                    e);
        }
    }

    private void sendSettlement(PaymentSettlementEventMessage event) {
        String key = event.getMerchantId().toString();
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(paymentSettlementTopic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error(
                                    "Settlement Kafka publish failed. topic={}, key={}, eventId={}, paymentId={}",
                                    paymentSettlementTopic,
                                    key,
                                    event.getEventId(),
                                    event.getPaymentId(),
                                    ex);
                            return;
                        }

                        log.info(
                                "Settlement Kafka publish success. topic={}, key={}, partition={}, offset={}, eventId={}",
                                result.getRecordMetadata().topic(),
                                key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                event.getEventId());
                    });
        } catch (JsonProcessingException e) {
            log.error(
                    "Settlement Kafka publish serialization failed. topic={}, key={}, eventId={}, paymentId={}",
                    paymentSettlementTopic,
                    key,
                    event.getEventId(),
                    event.getPaymentId(),
                    e);
        } catch (RuntimeException e) {
            log.error(
                    "Settlement Kafka publish invocation failed. topic={}, key={}, eventId={}, paymentId={}",
                    paymentSettlementTopic,
                    key,
                    event.getEventId(),
                    event.getPaymentId(),
                    e);
        }
    }

    // [be] 다윤 260607 03:00 | 이벤트 객체 생성
    private void publishPaymentEvent(
            PaymentEventType eventType,
            String title,
            Long userId,
            Long paymentId,
            String content) {
        if (userId == null || paymentId == null) {
            log.warn(
                    "Skip payment notification. eventType={}, userId={}, paymentId={}",
                    eventType,
                    userId,
                    paymentId);
            return;
        }

        LocalDateTime occurredAt = LocalDateTime.now();
        PaymentNotificationEventMessage event = PaymentNotificationEventMessage.builder()
                .eventId(createEventId(eventType, paymentId, userId))
                .eventType(eventType.name())
                .userId(userId)
                .title(title)
                .content(content)
                .paymentId(paymentId)
                .occurredAt(occurredAt)
                .build();

        publish(event);
    }

    private String buildPaymentCompletedContent(String orderName) {
        if (orderName == null || orderName.isBlank()) {
            return "결제가 완료되었습니다.";
        }
        return String.format("%s 결제가 완료되었습니다.", orderName);
    }

    private String buildPaymentCanceledContent(String orderName) {
        if (orderName == null || orderName.isBlank()) {
            return "결제가 취소되었습니다.";
        }
        return String.format("%s 결제가 취소되었습니다.", orderName);
    }

    // [be] 다윤 260607 03:00 | eventId 포맷
    private String createEventId(
            PaymentEventType eventType,
            Long paymentId,
            Long userId) {
        String action = switch (eventType) {
            case PAYMENT_COMPLETED -> "completed";
            case PAYMENT_CANCELED -> "canceled";
            case PAYMENT_SETTLEMENT_COMPLETED -> throw new IllegalArgumentException(
                    "Settlement event type is not supported for user notification eventId: " + eventType);
        };

        return String.format(
                "payment:%s:%d:%d",
                action,
                paymentId,
                userId);
    }

    // [be] 다윤 260608 | 결제 완료 정산 이벤트 발행
    public void publishPaymentSettlementCompleted(
            Long merchantId,
            Long paymentId,
            Long amount) {
        publishSettlementEvent(
                PaymentEventType.PAYMENT_SETTLEMENT_COMPLETED,
                merchantId,
                paymentId,
                amount);
    }

    private void publishSettlementEvent(
            PaymentEventType eventType,
            Long merchantId,
            Long paymentId,
            Long amount) {
        if (merchantId == null || paymentId == null || amount == null) {
            log.warn(
                    "Skip settlement notification. eventType={}, merchantId={}, paymentId={}, amount={}",
                    eventType,
                    merchantId,
                    paymentId,
                    amount);
            return;
        }

        LocalDateTime occurredAt = LocalDateTime.now();

        PaymentSettlementEventMessage event = PaymentSettlementEventMessage.builder()
                .eventId(createSettlementEventId(
                        eventType,
                        paymentId,
                        merchantId))
                .eventType(eventType.name())
                .merchantId(merchantId)
                .paymentId(paymentId)
                .amount(amount)
                .occurredAt(occurredAt)
                .build();

        publishSettlement(event);
    }

    private String createSettlementEventId(
            PaymentEventType eventType,
            Long paymentId,
            Long merchantId) {
        if (eventType != PaymentEventType.PAYMENT_SETTLEMENT_COMPLETED) {
            throw new IllegalArgumentException("Unsupported settlement event type: " + eventType);
        }

        return String.format(
                "payment:settlement:%s:%d:%d",
                "completed",
                paymentId,
                merchantId);
    }

}
