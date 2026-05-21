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
                // [be] 영은 260520 2120 | 인원 확정 전 세션 생성 단계에서는 자율 입력을 기본 배분 방식으로 저장
                .split_method(SplitMethod.CUSTOM)
                .status(DutchPayStatus.CREATED)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public void applyHostAuthorizationResult(boolean authorized) {
        // [be] 영은 260520 2046 | 대표자 가승인 결과는 CREATED 세션에서만 최초 상태 전이를 허용
        if (this.status != DutchPayStatus.CREATED) {
            throw new IllegalStateException("Host authorization result can only be applied to CREATED session");
        }

        this.status = authorized ? DutchPayStatus.IN_PROGRESS : DutchPayStatus.FAILED;
        this.updated_at = LocalDateTime.now();
    }

    public void requireHost(Long userId) {
        if (userId == null || !this.host_user_id.equals(userId)) {
            throw new IllegalStateException("Only host can update dutch pay session");
        }
    }

    public void requireInProgress() {
        if (this.status != DutchPayStatus.IN_PROGRESS) {
            throw new IllegalStateException("Dutch pay session is not in progress");
        }
    }

    public void changeSplitMethod(SplitMethod splitMethod, LocalDateTime now) {
        if (splitMethod == null || now == null) {
            throw new IllegalArgumentException("splitMethod and now must not be null");
        }
        requireInProgress();

        this.split_method = splitMethod;
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
        FAILED
    }
}
