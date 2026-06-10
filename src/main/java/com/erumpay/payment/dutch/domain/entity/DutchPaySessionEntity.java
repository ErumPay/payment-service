package com.erumpay.payment.dutch.domain.entity;

import java.time.LocalDateTime;

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
@Table(name = "dutch_pay_sessions")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class DutchPaySessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long session_id;

    private String dutch_order_no;
    private Long host_user_id;
    private Long merchant_id;
    private String order_name;
    private Long host_auth_payment_id;

    private Long total_amount;

    @Enumerated(EnumType.STRING)
    private SplitMethod split_method;

    @Enumerated(EnumType.STRING)
    private DutchPayStatus status;

    private LocalDateTime timeout_at;
    private LocalDateTime warning_1_sent_at;
    private LocalDateTime warning_2_sent_at;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private LocalDateTime completed_at;

    // [be] 영은 260523 1120 | core prepare 이후 CREATED 세션을 만들고 기본 배분 방식은 CUSTOM으로 둔다
    public static DutchPaySessionEntity created(
            String dutchOrderNo,
            Long hostAuthPaymentId,
            Long hostUserId,
            Long merchantId,
            String orderName,
            Long totalAmount,
            LocalDateTime now) {
        return DutchPaySessionEntity.builder()
                .dutch_order_no(dutchOrderNo)
                .host_auth_payment_id(hostAuthPaymentId)
                .host_user_id(hostUserId)
                .merchant_id(merchantId)
                .order_name(orderName)
                .total_amount(totalAmount)
                .split_method(SplitMethod.CUSTOM)
                .status(DutchPayStatus.CREATED)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    // [be] 영은 260523 1120 | 대표자 가승인 결과에 따라 세션을 IN_PROGRESS 또는 FAILED로 전환한다
    public void applyHostAuthorizationResult(boolean authorized) {
        if (this.status != DutchPayStatus.CREATED) {
            throw new IllegalStateException("Host authorization result can only be applied to CREATED session");
        }

        this.status = authorized ? DutchPayStatus.IN_PROGRESS : DutchPayStatus.FAILED;
        this.updated_at = LocalDateTime.now();
    }

    // [be] 영은 260523 1120 | 대표자만 가능한 작업에서 요청 user_id를 검증한다
    public void requireHost(Long userId) {
        if (userId == null || !this.host_user_id.equals(userId)) {
            throw new IllegalStateException("Only host can update dutch pay session");
        }
    }

    // [be] 영은 260523 1120 | 초대/배분/금액 입력은 IN_PROGRESS 세션에서만 허용한다
    public void requireInProgress() {
        if (this.status != DutchPayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Dutch pay session is not in progress");
        }
    }

    // [be] 영은 260523 1120 | 대표자가 배분 방식을 변경하면 세션 수정 시각을 함께 갱신한다
    public void changeSplitMethod(SplitMethod splitMethod, LocalDateTime now) {
        if (splitMethod == null || now == null) {
            throw new IllegalArgumentException("splitMethod and now must not be null");
        }
        requireInProgress();

        this.split_method = splitMethod;
        this.updated_at = now;
    }

    // [be] 영은 260601 | 대표자 최종 결제까지 끝난 세션을 완료 상태로 전환한다.
    public void complete(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (this.status == DutchPayStatus.COMPLETED) {
            return;
        }
        if (this.status != DutchPayStatus.IN_PROGRESS && this.status != DutchPayStatus.TIMEOUT_HANDLED) {
            throw new IllegalStateException("Dutch pay session cannot be completed from current status");
        }

        this.status = DutchPayStatus.COMPLETED;
        this.completed_at = now;
        this.updated_at = now;
    }

    public void markWarning1Sent(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        requireInProgress();

        if (this.warning_1_sent_at == null) {
            this.warning_1_sent_at = now;
            this.updated_at = now;
        }
    }

    public void markWarning2Sent(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        requireInProgress();

        if (this.warning_2_sent_at == null) {
            this.warning_2_sent_at = now;
            this.updated_at = now;
        }
    }

    public void timeoutHandled(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        requireInProgress();

        this.status = DutchPayStatus.TIMEOUT_HANDLED;
        this.timeout_at = now;
        this.updated_at = now;
    }

    public void cancel(LocalDateTime now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        if (this.status != DutchPayStatus.CREATED && this.status != DutchPayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Dutch pay session cannot be canceled from current status");
        }

        this.status = DutchPayStatus.CANCELED;
        this.updated_at = now;
    }

    public enum SplitMethod {
        EQUAL,
        CUSTOM
    }

    public enum DutchPayStatus {
        CREATED,
        IN_PROGRESS,
        COMPLETED,
        TIMEOUT_HANDLED,
        CANCELED,
        FAILED
    }
}
