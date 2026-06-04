package com.erumpay.payment.notification.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.notification.dto.PaymentNotificationEventMessage;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class PaymentNotificationEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.dutch-event:dutch.event}")
    private String dutchTopic;

    @Value("${app.kafka.topics.remote-event:remote.event}")
    private String remoteTopic;

    public PaymentNotificationEventPublisher(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps(bootstrapServers)));
        this.objectMapper = objectMapper;
    }

    private Map<String, Object> producerProps(String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return props;
    }

    public void publishRemoteRequested(RemotePayCreateResponse response) {
        if (response == null || response.getTarget_user_id() == null) {
            return;
        }
        publishRemote(
                response,
                "REMOTE_REQUESTED",
                response.getTarget_user_id(),
                "원격결제 요청이 도착했습니다.",
                "친구가 원격결제를 요청했습니다.");
    }

    public void publishRemoteRejected(RemotePayCreateResponse response) {
        if (response == null || response.getRequester_user_id() == null) {
            return;
        }
        publishRemote(
                response,
                "REMOTE_REJECTED",
                response.getRequester_user_id(),
                "원격결제가 거절되었습니다.",
                "대리결제자가 원격결제 요청을 거절했습니다.");
    }

    public void publishRemoteCompleted(RemotePayCreateResponse response) {
        if (response == null || response.getRequester_user_id() == null) {
            return;
        }
        publishRemote(
                response,
                "REMOTE_COMPLETED",
                response.getRequester_user_id(),
                "원격결제가 완료되었습니다.",
                "대리결제자의 결제가 완료되었습니다.");
    }

    public void publishDutchInvited(Long sessionId, Long userId, String orderName) {
        publishDutch(
                sessionId,
                userId,
                null,
                "DUTCHPAY_INVITED",
                "더치페이 초대가 도착했습니다.",
                orderName == null ? "더치페이 초대가 도착했습니다." : orderName + " 더치페이 초대가 도착했습니다.");
    }

    public void publishDutchConfirmed(Long sessionId, Long userId, String orderName) {
        publishDutch(
                sessionId,
                userId,
                null,
                "DUTCHPAY_CONFIRMED",
                "더치페이 인원이 확정되었습니다.",
                orderName == null ? "더치페이 결제 요청을 확인해주세요." : orderName + " 결제 요청을 확인해주세요.");
    }

    public void publishDutchTimeoutWarning1(Long sessionId, Long userId, Long paymentId) {
        publishDutch(
                sessionId,
                userId,
                paymentId,
                "DUTCHPAY_1ST_TIMEDOUT",
                "더치페이 결제 시간이 얼마 남지 않았습니다.",
                "아직 결제하지 않은 참여자에게 결제를 안내해주세요.");
    }

    public void publishDutchTimeoutWarning2(Long sessionId, Long userId, Long paymentId) {
        publishDutch(
                sessionId,
                userId,
                paymentId,
                "DUTCHPAY_2ST_TIMEDOUT",
                "더치페이 결제 마감이 임박했습니다.",
                "곧 미결제 금액이 대표자 최종 부담금으로 정리됩니다.");
    }

    public void publishDutchCompleted(Long sessionId, Long userId, Long paymentId) {
        publishDutch(
                sessionId,
                userId,
                paymentId,
                "DUTCHPAY_COMPLETED",
                "더치페이가 완료되었습니다.",
                "더치페이 결제가 완료되었습니다.");
    }

    private void publishRemote(
            RemotePayCreateResponse response,
            String eventType,
            Long userId,
            String title,
            String content) {
        Long paymentId = response.getPayer_payment_id() == null
                ? response.getPayment_id()
                : response.getPayer_payment_id();
        publish(
                remoteTopic,
                "remote:%s:%d:%d".formatted(eventType, response.getRequest_id(), userId),
                eventType,
                userId,
                title,
                content,
                paymentId,
                "remote:%d".formatted(response.getRequest_id()));
    }

    private void publishDutch(
            Long sessionId,
            Long userId,
            Long paymentId,
            String eventType,
            String title,
            String content) {
        if (sessionId == null || userId == null) {
            return;
        }
        publish(
                dutchTopic,
                "dutch:%s:%d:%d".formatted(eventType, sessionId, userId),
                eventType,
                userId,
                title,
                content,
                paymentId,
                "dutch:%d".formatted(sessionId));
    }

    private void publish(
            String topic,
            String eventId,
            String eventType,
            Long userId,
            String title,
            String content,
            Long paymentId,
            String correlationId) {
        PaymentNotificationEventMessage message = PaymentNotificationEventMessage.builder()
                .eventId(eventId)
                .eventType(eventType)
                .userId(userId)
                .title(title)
                .content(content)
                .paymentId(paymentId)
                .occurredAt(LocalDateTime.now())
                .correlationId(correlationId)
                .build();

        Runnable sendTask = () -> send(topic, eventId, message);
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

    private void send(String topic, String key, PaymentNotificationEventMessage message) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.warn("Notification event serialization failed. topic={}, key={}", topic, key, e);
        } catch (RuntimeException e) {
            log.warn("Notification event publish failed. topic={}, key={}", topic, key, e);
        }
    }
}
