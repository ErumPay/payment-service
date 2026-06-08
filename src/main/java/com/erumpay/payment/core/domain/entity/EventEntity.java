package com.erumpay.payment.core.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_events")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long event_id;

    private Long payment_id;
    private Long pg_txn_id;
    private Long pg_group_id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EventType event_type;

    private String fail_code;
    private LocalDateTime created_at;

    @Enumerated(EnumType.STRING)
    private ActorType actor_type;

    public enum EventType {
        CREATED,
        PAY_PENDING,
        PG_PENDING,
        PAID,
        CANCEL_REQUESTED,
        CANCELED,
        FAILED,
        EXPIRED,
        AUTHORIZED,
        VOIDED
    }

    public enum ActorType {
        USER,
        SYSTEM,
        PG,
        ADMIN
    }
}
