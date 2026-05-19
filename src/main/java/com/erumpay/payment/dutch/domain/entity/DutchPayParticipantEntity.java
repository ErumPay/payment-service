package com.erumpay.payment.dutch.domain.entity;

import java.time.LocalDateTime;

import com.erumpay.payment.core.domain.entity.OrderEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "dutch_pay_participants", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "session_id", "user_id" })
})
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participant_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private DutchPaySessionEntity session;

    private Long user_id;
    private Long amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private OrderEntity payment;

    @Enumerated(EnumType.STRING)
    private ParticipantStatus status;

    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime paid_at;

    public static DutchPayParticipantEntity invited(
            DutchPaySessionEntity session,
            Long userId,
            Long amount,
            LocalDateTime now) {
        return DutchPayParticipantEntity.builder()
                .session(session)
                .user_id(userId)
                .amount(amount)
                .status(ParticipantStatus.INVITED)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public enum ParticipantStatus {
        INVITED,
        REJECTED,
        PENDING,
        PAID,
        TIMEOUT,
        HOST_PAID
    }
}
