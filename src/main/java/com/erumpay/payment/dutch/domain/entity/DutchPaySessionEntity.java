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
import jakarta.persistence.OneToOne;
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

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_auth_payment_id", unique = true)
    private OrderEntity host_auth_payment;

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
            Long hostUserId,
            Long merchantId,
            String orderName,
            Long totalAmount,
            LocalDateTime now) {
        return DutchPaySessionEntity.builder()
                .dutch_order_no(dutchOrderNo)
                .host_user_id(hostUserId)
                .merchant_id(merchantId)
                .order_name(orderName)
                .total_amount(totalAmount)
                .status(DutchPayStatus.CREATED)
                .created_at(now)
                .updated_at(now)
                .build();
    }

    public void authorizeHostPayment(OrderEntity hostAuthPayment) {
        this.host_auth_payment = hostAuthPayment;
        this.status = DutchPayStatus.IN_PROGRESS;
        this.updated_at = LocalDateTime.now();
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
