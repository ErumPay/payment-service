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

    // [be] 영은 260527 1005 | 원격결제 요청 최초 상태를 만든다. 이 시점에는 아직 결제자가 결제 화면에 진입하지 않아 payment_order가 없다.
    // [be] 영은 260527 1005 | 요청자/대상자/금액/만료 시간을 엔티티 생성 경계에서 검증해 잘못된 PENDING 요청 저장을 막는다.
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

    // [be] 영은 260527 1430 | Core /payment/prepare에서 준비된 payment_order를 원격결제 요청에 연결한다.
    // [be] 영은 260527 1430 | 같은 사용자가 여러 요청을 받아도 request_id 기준으로 연결하므로 다른 원격결제와 payment_id가 섞이지 않는다.
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

    // [be] 영은 260527 1040 | 대상자는 결제 준비 전 PENDING 요청만 거절할 수 있다.
    // [be] 영은 260527 1040 | payment_order가 연결된 뒤에는 결제가 시작된 상태라 요청 거절로 되돌리지 않는다.
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

    // [be] 영은 260527 1050 | 요청자는 결제 준비 전 PENDING 요청만 취소할 수 있다.
    // [be] 영은 260527 1050 | 취소는 requester_user_id만 가능하게 해 대상자가 임의로 요청을 취소하지 못하게 한다.
    public void cancel(Long userId, LocalDateTime now) {
        if (userId == null || now == null || !this.requester_user_id.equals(userId)) {
            throw new IllegalStateException("only requester can cancel remote payment request");
        }
        requirePending(now);
        requirePaymentNotStarted();

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

    // [be] 영은 260527 1035 | 모든 전이는 PENDING에서만 시작한다. 만료 시간이 지났으면 즉시 EXPIRED로 표시하고 전이를 중단한다.
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

    // [be] 영은 260527 1045 | payment_order가 연결되면 결제 플로우가 시작된 것으로 보고 요청 취소/거절을 막는다.
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
