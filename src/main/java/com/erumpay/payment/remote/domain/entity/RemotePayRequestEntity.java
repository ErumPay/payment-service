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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payment_remote_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_remote_requests_payment",
                columnNames = "payment_id"))
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
    private Long source_payment_id;

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

    // 요청자가 원격결제 버튼을 누른 직후의 임시 요청이다.
    // 아직 대리결제자를 고르기 전이라 target_user_id와 payer payment는 null이고, request_id만 먼저 발급된다.
    public static RemotePayRequestEntity draftFromCore(
            Long requesterUserId,
            Long sourcePaymentId,
            Long amount,
            String description,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        if (requesterUserId == null || sourcePaymentId == null || amount == null || expiresAt == null || now == null) {
            throw new IllegalArgumentException("required remote payment draft fields must not be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be after now");
        }

        return RemotePayRequestEntity.builder()
                .requester_user_id(requesterUserId)
                .source_payment_id(sourcePaymentId)
                .amount(amount)
                .description(description)
                .status(RemotePayStatus.DRAFT)
                .expires_at(expiresAt)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    // targetUserId를 이미 알고 있을 때 PENDING 요청을 바로 만드는 직접 생성/호환용 경로다.
    // 현재 화면 기본 흐름은 draftFromCore -> assignTarget -> assignPayment 순서다.
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

    // targetUserId를 이미 알고 있을 때 Core payment와 PENDING 요청을 한 번에 묶는 기존 내부 생성 경로다.
    // target 후선택 흐름에서는 source payment만 가진 DRAFT를 먼저 만들고, 대리결제자 prepare 후 assignPayment로 묶는다.
    public static RemotePayRequestEntity pendingFromCore(
            Long requesterUserId,
            Long targetUserId,
            CoreEntity payment,
            String description,
            LocalDateTime expiresAt,
            LocalDateTime now) {
        if (requesterUserId == null || targetUserId == null || payment == null || expiresAt == null || now == null) {
            throw new IllegalArgumentException("required remote payment fields must not be null");
        }
        if (payment.getPaymentId() == null || payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("payment must be persisted and have a positive amount");
        }
        if (payment.getChannel_type() != CoreEntity.ChannelType.ONLINE) {
            throw new IllegalArgumentException("remote payment requires online payment");
        }
        if (payment.getUserId() != null && !targetUserId.equals(payment.getUserId())) {
            throw new IllegalArgumentException("payment user must match remote payment target");
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
                .source_payment_id(payment.getPaymentId())
                .payment(payment)
                .amount(payment.getAmount())
                .description(description)
                .status(RemotePayStatus.PENDING)
                .expires_at(expiresAt)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    // 요청자만 DRAFT 요청에 대리결제자를 지정할 수 있다. 성공하면 PENDING으로 전환된다.
    public void assignTarget(Long requesterUserId, Long targetUserId, LocalDateTime now) {
        if (requesterUserId == null || targetUserId == null || now == null) {
            throw new IllegalArgumentException("requesterUserId, targetUserId and now must not be null");
        }
        if (!this.requester_user_id.equals(requesterUserId)) {
            throw new IllegalStateException("only requester can assign remote payment target");
        }
        if (requesterUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("requester and target must be different");
        }
        if (this.status != RemotePayStatus.DRAFT) {
            throw new IllegalStateException("remote payment request target can be assigned only in draft status");
        }
        if (this.target_user_id != null && !this.target_user_id.equals(targetUserId)) {
            throw new IllegalStateException("remote payment request already has another target");
        }
        if (this.expires_at != null && !this.expires_at.isAfter(now)) {
            throw new IllegalStateException("remote payment request is expired");
        }

        this.target_user_id = targetUserId;
        this.status = RemotePayStatus.PENDING;
        this.updated_at = now;
    }

    public void acceptTarget(Long targetUserId, LocalDateTime now) {
        // [be] 영은 260610 | 공유 링크 수신자가 DRAFT 요청을 직접 수락하면 본인을 대리결제자로 지정한다.
        if (targetUserId == null || now == null) {
            throw new IllegalArgumentException("targetUserId and now must not be null");
        }
        if (this.requester_user_id.equals(targetUserId)) {
            throw new IllegalArgumentException("requester and target must be different");
        }
        if (this.status != RemotePayStatus.DRAFT) {
            throw new IllegalStateException("remote payment request target can be assigned only in draft status");
        }
        if (this.target_user_id != null && !this.target_user_id.equals(targetUserId)) {
            throw new IllegalStateException("remote payment request already has another target");
        }
        if (this.expires_at != null && !this.expires_at.isAfter(now)) {
            throw new IllegalStateException("remote payment request is expired");
        }

        this.target_user_id = targetUserId;
        this.status = RemotePayStatus.PENDING;
        this.updated_at = now;
    }

    // 대리결제자가 만든 실제 결제 주문을 PENDING 원격결제 요청에 연결한다.
    // target_user_id, payment.userId, amount가 모두 맞아야 이후 결제 요청으로 넘어갈 수 있다.
    public void assignPayment(Long targetUserId, CoreEntity payment, LocalDateTime now) {
        if (targetUserId == null || payment == null || now == null) {
            throw new IllegalArgumentException("targetUserId, payment and now must not be null");
        }
        if (!this.target_user_id.equals(targetUserId)) {
            throw new IllegalStateException("only target user can prepare remote payment");
        }
        if (!this.amount.equals(payment.getAmount())) {
            throw new IllegalStateException("payment amount does not match remote request");
        }
        if (!targetUserId.equals(payment.getUserId())) {
            throw new IllegalStateException("payment user is not remote payment target");
        }

        requirePending(now);
        if (this.payment != null && !this.payment.getPaymentId().equals(payment.getPaymentId())) {
            throw new IllegalStateException("remote payment request already has another payment");
        }

        this.payment = payment;
        this.updated_at = now;
    }

    // [be] 영은 260527 1040 | 대상자는 PENDING 상태의 원격결제 요청만 거절할 수 있다.
    // [be] 영은 260528 1040 | B안에서는 payment_id가 처음부터 존재하므로 거절 가능 여부를 payment 연결 여부로 판단하지 않는다.
    public void reject(Long userId, String reason, LocalDateTime now) {
        if (userId == null || now == null || !this.target_user_id.equals(userId)) {
            throw new IllegalStateException("only target user can reject remote payment request");
        }
        requirePending(now);

        this.status = RemotePayStatus.REJECTED_BY_PAYER;
        this.reject_reason = reason;
        this.updated_at = now;
    }

    // [be] 영은 260527 1050 | 요청자는 PENDING 상태의 원격결제 요청만 취소할 수 있다.
    // [be] 영은 260528 1040 | 취소는 requester_user_id만 가능하게 해 대상자가 임의로 요청을 취소하지 못하게 한다.
    public void cancel(Long userId, LocalDateTime now) {
        if (userId == null || now == null || !this.requester_user_id.equals(userId)) {
            throw new IllegalStateException("only requester can cancel remote payment request");
        }
        requirePending(now);

        this.status = RemotePayStatus.CANCELLED_BY_REQUESTER;
        this.updated_at = now;
    }

    // [be] 영은 260527 1450 | Core가 PG 결제 성공 후 호출해 요청 상태를 COMPLETED로 확정한다.
    // [be] 영은 260527 1450 | 연결된 payment_id와 다른 결제 결과로 완료 처리되는 것을 막아 정산 추적성을 지킨다.
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

    // [be] 영은 260528 1120 | 만료 배치가 PENDING 요청을 EXPIRED로 확정할 때 사용하는 상태 전이다.
    // [be] 영은 260528 1120 | 결제 성공/거절/취소된 요청은 다시 만료 처리하지 않아 상태 전이의 단방향성을 지킨다.
    public void expire(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (this.status != RemotePayStatus.DRAFT && this.status != RemotePayStatus.PENDING) {
            throw new IllegalStateException("remote payment request is not expirable");
        }
        if (this.expires_at != null && this.expires_at.isAfter(now)) {
            throw new IllegalStateException("remote payment request is not expired yet");
        }

        this.status = RemotePayStatus.EXPIRED;
        this.updated_at = now;
    }

    // [be] 영은 260527 1440 | PG 요청 직전 원격결제 요청이 아직 결제 가능한 상태인지 확인한다.
    // [be] 영은 260527 1440 | prepare 이후 cancel/reject/expire 상태가 되면 실제 결제 요청으로 넘어가지 못하게 한다.
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

    // [be] 영은 260527 1035 | 결제/거절/취소/완료 전이는 PENDING에서만 시작한다.
    private void requirePending(LocalDateTime now) {
        if (this.status != RemotePayStatus.PENDING) {
            throw new IllegalStateException("remote payment request is not pending");
        }
        if (this.expires_at != null && !this.expires_at.isAfter(now)) {
            throw new IllegalStateException("remote payment request is expired");
        }
    }

    // DRAFT: 요청자가 원격결제를 선택했지만 아직 대리결제자를 고르지 않은 상태.
    // PENDING: 대리결제자가 지정되어 결제/거절/취소를 기다리는 상태.
    public enum RemotePayStatus {
        DRAFT,
        PENDING,
        COMPLETED,
        REJECTED_BY_PAYER,
        CANCELLED_BY_REQUESTER,
        EXPIRED
    }
}
