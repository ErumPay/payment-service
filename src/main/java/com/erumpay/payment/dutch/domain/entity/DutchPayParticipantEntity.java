package com.erumpay.payment.dutch.domain.entity;

import java.time.LocalDateTime;

import com.erumpay.payment.core.domain.entity.CoreEntity;

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
    private CoreEntity payment;

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
        if (session == null || userId == null || now == null) {
            throw new IllegalArgumentException("session, userId, and now must not be null");
        }
        if (amount != null && amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        return DutchPayParticipantEntity.builder()
                .session(session)
                .user_id(userId)
                .amount(amount)
                .status(ParticipantStatus.INVITED)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public static DutchPayParticipantEntity host(
            DutchPaySessionEntity session,
            Long userId,
            LocalDateTime now) {
        if (session == null || userId == null || now == null) {
            throw new IllegalArgumentException("session, userId, and now must not be null");
        }

        return DutchPayParticipantEntity.builder()
                .session(session)
                .user_id(userId)
                .status(ParticipantStatus.PENDING)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public void confirm(LocalDateTime now) {
        if (this.status == ParticipantStatus.INVITED) {
            this.status = ParticipantStatus.PENDING;
            this.updated_at = now;
        }
    }

    public void reject(LocalDateTime now) {
        if (this.status != ParticipantStatus.INVITED && this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only invited or pending participant can reject");
        }

        this.status = ParticipantStatus.REJECTED;
        this.amount = null;
        this.updated_at = now;
    }

    public void updateAmount(Long amount, LocalDateTime now) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant amount can be updated");
        }

        this.amount = amount;
        this.updated_at = now;
    }

    public void assignAmount(Long amount, LocalDateTime now) {
        if (amount == null || amount < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant amount can be assigned");
        }

        this.amount = amount;
        this.updated_at = now;
    }

    public void clearAmount(LocalDateTime now) {
        if (this.status == ParticipantStatus.PENDING) {
            this.amount = null;
            this.updated_at = now;
        }
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
