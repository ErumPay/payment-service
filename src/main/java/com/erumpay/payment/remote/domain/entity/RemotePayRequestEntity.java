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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_remote_requests")
@Builder(access = AccessLevel.PRIVATE)
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after now");
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

    public void assignPayment(CoreEntity payment, LocalDateTime now) {
        if (payment == null || now == null) {
            throw new IllegalArgumentException("payment and now must not be null");
        }
        requirePending(now);
        if (this.payment != null && !this.payment.getPaymentId().equals(payment.getPaymentId())) {
            throw new IllegalStateException("remote payment request already has another payment");
        }

        this.payment = payment;
        this.updated_at = now;
    }

    public void reject(Long userId, String reason, LocalDateTime now) {
        if (userId == null || now == null || !this.target_user_id.equals(userId)) {
            throw new IllegalStateException("only target user can reject remote payment request");
        }
        requirePending(now);
        requirePaymentNotStarted();

        this.status = RemotePayStatus.REJECTED_BY_PAYER;
        this.reject_reason = reason;
        this.updated_at = now;
    }

    public void cancel(Long userId, LocalDateTime now) {
        if (userId == null || now == null || !this.requester_user_id.equals(userId)) {
            throw new IllegalStateException("only requester can cancel remote payment request");
        }
        requirePending(now);
        requirePaymentNotStarted();

        this.status = RemotePayStatus.CANCELLED_BY_REQUESTER;
        this.updated_at = now;
    }

    public void complete(CoreEntity payment, LocalDateTime now) {
        if (payment == null || now == null) {
            throw new IllegalArgumentException("payment and now must not be null");
        }
        requirePending(now);
        if (this.payment == null || !this.payment.getPaymentId().equals(payment.getPaymentId())) {
            throw new IllegalStateException("remote payment request is not connected to payment");
        }

        this.status = RemotePayStatus.COMPLETED;
        this.completed_at = now;
        this.updated_at = now;
    }

    public void requirePayable(CoreEntity payment, LocalDateTime now) {
        if (payment == null || now == null) {
            throw new IllegalArgumentException("payment and now must not be null");
        }
        requirePending(now);
        if (this.payment == null || !this.payment.getPaymentId().equals(payment.getPaymentId())) {
            throw new IllegalStateException("remote payment request is not connected to payment");
        }
        if (!this.target_user_id.equals(payment.getUserId())) {
            throw new IllegalStateException("payment user is not remote payment target");
        }
    }

    private void requirePending(LocalDateTime now) {
        if (this.status != RemotePayStatus.PENDING) {
            throw new IllegalStateException("remote payment request is not pending");
        }
        if (this.expires_at != null && !this.expires_at.isAfter(now)) {
            this.status = RemotePayStatus.EXPIRED;
            this.updated_at = now;
            throw new IllegalStateException("remote payment request is expired");
        }
    }

    private void requirePaymentNotStarted() {
        if (this.payment != null) {
            throw new IllegalStateException("remote payment request already has payment");
        }
    }

    public enum RemotePayStatus {
        PENDING,
        COMPLETED,
        REJECTED_BY_PAYER,
        CANCELLED_BY_REQUESTER,
        EXPIRED
    }
}
