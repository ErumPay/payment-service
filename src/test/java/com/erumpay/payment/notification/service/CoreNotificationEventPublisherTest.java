package com.erumpay.payment.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CoreNotificationEventPublisherTest {

    private static final String PAYMENT_TOPIC = "payment.event";
    private static final String SETTLEMENT_TOPIC = "payment.settlement.event";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private ObjectMapper objectMapper;
    private CoreNotificationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new CoreNotificationEventPublisher(
                kafkaTemplate,
                objectMapper,
                PAYMENT_TOPIC,
                SETTLEMENT_TOPIC);
    }

    @Test
    void publishPaymentSettlementCompletedSendsSettlementEventToDedicatedTopic() throws Exception {
        when(kafkaTemplate.send(eq(SETTLEMENT_TOPIC), eq("7"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult(SETTLEMENT_TOPIC, "7", "{}")));

        publisher.publishPaymentSettlementCompleted(7L, 101L, 25000L);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(SETTLEMENT_TOPIC), eq("7"), payloadCaptor.capture());
        verify(kafkaTemplate, never()).send(eq(PAYMENT_TOPIC), anyString(), anyString());

        JsonNode payload = objectMapper.readTree(payloadCaptor.getValue());
        assertThat(payload.get("eventType").asText()).isEqualTo("PAYMENT_SETTLEMENT_COMPLETED");
        assertThat(payload.get("merchantId").asLong()).isEqualTo(7L);
        assertThat(payload.get("paymentId").asLong()).isEqualTo(101L);
        assertThat(payload.get("amount").asLong()).isEqualTo(25000L);
        assertThat(payload.get("eventId").asText()).startsWith("payment:settlement:completed:101:7:");
        assertThat(payload.hasNonNull("occurredAt")).isTrue();
    }

    private SendResult<String, String> sendResult(String topic, String key, String payload) {
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, key, payload);
        RecordMetadata recordMetadata = new RecordMetadata(
                new TopicPartition(topic, 0),
                0,
                0,
                System.currentTimeMillis(),
                0,
                0);
        return new SendResult<>(producerRecord, recordMetadata);
    }
}
