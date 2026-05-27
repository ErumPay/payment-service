package com.erumpay.payment.remote.domain.entity;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_remote_requests")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RemotePayRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long request_id;

    private Long requester_user_id;
    private Long target_user_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private CoreEntity payment;

    private Long amount;
    private String description;

    @Enumerated(EnumType.STRING)
    private RemotePayStatus status;

    private String reject_reason;
    private LocalDateTime expires_at;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime completed_at;

    public static RemotePayRequestEntity pending(
            Long requesterUserId,
            Long targetUserId,
            Long amount,
            String description,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        if (requesterUserId == null || targetUserId == null || amount == null || expiresAt == null || now == null) {
            throw new IllegalArgumentException("required remote payment fields must not be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (requesterUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("requester and target must be different");
        }

        return RemotePayRequestEntity.builder()
                .requester_user_id(requesterUserId)
                .target_user_id(targetUserId)
                .amount(amount)
                .description(description)
                .status(RemotePayStatus.PENDING)
                .expires_at(expiresAt)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public enum RemotePayStatus {
        PENDING,
        COMPLETED,
        REJECTED_BY_PAYER,
        CANCELLED_BY_REQUESTER,
        EXPIRED
    }
}
