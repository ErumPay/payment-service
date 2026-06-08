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

    public static final String ERROR_PAYMENT_ALREADY_ASSIGNED = "Participant payment is already assigned";
    public static final String ERROR_AMOUNT_MISMATCH = "Participant amount and payment amount must match";

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

    // [be] 영은 260523 1120 | 대표자가 초대한 참여자를 INVITED 상태로 생성한다
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

    // [be] 영은 260523 1120 | 대표자는 세션 생성 시 즉시 PENDING 참여자로 포함한다
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

    // [be] 영은 260523 1120 | 대표자 인원 확정 후 초대 상태 참여자를 결제 대기 상태로 전환한다
    public void confirm(LocalDateTime now) {
        validateNow(now);

        if (this.status == ParticipantStatus.INVITED) {
            this.status = ParticipantStatus.PENDING;
            this.updated_at = now;
        }
    }

    // [be] 영은 260523 1120 | 초대 거절 시 참여자 상태와 입력 금액을 초기화한다
    public void reject(LocalDateTime now) {
        validateNow(now);

        if (this.status != ParticipantStatus.INVITED && this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only invited or pending participant can reject");
        }

        this.status = ParticipantStatus.REJECTED;
        this.amount = null;
        this.updated_at = now;
    }

    // [be] 영은 260523 1120 | CUSTOM 배분에서 참여자가 본인 부담 금액을 직접 입력하거나 수정한다
    public void updateAmount(Long amount, LocalDateTime now) {
        validateNow(now);

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant amount can be updated");
        }

        this.amount = amount;
        this.updated_at = now;
    }

    // [be] 영은 260523 1120 | EQUAL 배분 또는 대표자 잔액 계산 결과를 참여자 금액으로 반영한다
    public void assignAmount(Long amount, LocalDateTime now) {
        validateNow(now);

        if (amount == null || amount < 0) {
            throw new IllegalArgumentException("amount must be zero or positive");
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant amount can be assigned");
        }

        this.amount = amount;
        this.updated_at = now;
    }

    // [be] 영은 260523 1120 | CUSTOM 전환 시 참여자가 다시 입력할 수 있도록 금액을 비운다
    public void clearAmount(LocalDateTime now) {
        validateNow(now);

        if (this.status == ParticipantStatus.PENDING) {
            this.amount = null;
            this.updated_at = now;
        }
    }

    // [be] 영은 260526 1620 | 참여자 결제 주문을 연결해 같은 참여자가 결제를 중복 생성하지 못하게 한다
    public void startPayment(CoreEntity payment, LocalDateTime now) {
        validateNow(now);

        if (payment == null || payment.getPaymentId() == null) {
            throw new IllegalArgumentException("payment must not be null");
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant can start payment");
        }
        if (this.payment != null) {
            throw new IllegalStateException(ERROR_PAYMENT_ALREADY_ASSIGNED);
        }
        if (this.amount == null || !this.amount.equals(payment.getAmount())) {
            throw new IllegalArgumentException(ERROR_AMOUNT_MISMATCH);
        }

        this.payment = payment;
        this.updated_at = now;
    }

    // [be] 영은 260526 1620 | PG 승인 완료 이벤트가 도착하면 참여자 부담금 결제를 완료 상태로 전환한다
    public void completePayment(Long paymentId, LocalDateTime now) {
        validateNow(now);

        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (this.status == ParticipantStatus.PAID) {
            return;
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant payment can be completed");
        }
        if (this.payment == null || !this.payment.getPaymentId().equals(paymentId)) {
            throw new IllegalStateException("Participant payment does not match");
        }

        this.status = ParticipantStatus.PAID;
        this.paid_at = now;
        this.updated_at = now;
    }

    // [be] 조보름 260607 1045 | 참여자 결제 실패 시 실패 금액을 보존한 채 대표자 부담금 재계산 대상으로 전환한다
    public void failPayment(Long paymentId, LocalDateTime now) {
        validateNow(now);

        if (paymentId == null) {
            throw new IllegalArgumentException("paymentId must not be null");
        }
        if (this.status == ParticipantStatus.REJECTED) {
            return;
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending participant payment can be failed");
        }
        if (this.payment == null || !this.payment.getPaymentId().equals(paymentId)) {
            throw new IllegalStateException("Participant payment does not match");
        }

        this.status = ParticipantStatus.REJECTED;
        this.paid_at = null;
        this.updated_at = now;
    }

    // [be] 영은 260601 | 대표자 최종 부담금 결제가 완료되면 대표자 참여자 row를 HOST_PAID로 전환한다.
    public void completeHostFinalPayment(CoreEntity payment, LocalDateTime now) {
        validateNow(now);

        if (payment == null || payment.getPaymentId() == null) {
            throw new IllegalArgumentException("payment must not be null");
        }
        if (this.status == ParticipantStatus.HOST_PAID) {
            return;
        }
        if (this.status != ParticipantStatus.PENDING) {
            throw new IllegalStateException("Only pending host participant can complete final payment");
        }
        if (this.amount == null || !this.amount.equals(payment.getAmount())) {
            throw new IllegalArgumentException(ERROR_AMOUNT_MISMATCH);
        }
        if (this.payment != null && !this.payment.getPaymentId().equals(payment.getPaymentId())) {
            throw new IllegalStateException(ERROR_PAYMENT_ALREADY_ASSIGNED);
        }

        this.payment = payment;
        this.status = ParticipantStatus.HOST_PAID;
        this.paid_at = now;
        this.updated_at = now;
    }

    public void timeout(LocalDateTime now) {
        validateNow(now);

        if (this.status == ParticipantStatus.PAID
                || this.status == ParticipantStatus.REJECTED
                || this.status == ParticipantStatus.TIMEOUT
                || this.status == ParticipantStatus.HOST_PAID) {
            return;
        }
        if (this.status != ParticipantStatus.PENDING && this.status != ParticipantStatus.INVITED) {
            throw new IllegalStateException("Only invited or pending participant can be timed out");
        }

        this.status = ParticipantStatus.TIMEOUT;
        this.amount = null;
        this.updated_at = now;
    }

    // [be] 영은 260523 1120 | 상태 변경 시각이 누락되지 않도록 도메인 메서드 공통 검증을 수행한다
    private void validateNow(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
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
