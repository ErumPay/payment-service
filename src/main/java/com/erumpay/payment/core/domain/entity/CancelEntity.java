package com.erumpay.payment.core.domain.entity;

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
@Table(name = "payment_cancel_history")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CancelEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cancel_id;

    private Long payment_id;
    private Long amount;
    private Long pg_txn_id;
    private String pg_cancel_approval_num;
    private String fail_code;

    @Enumerated(EnumType.STRING)
    private CancelStatus cancel_status;

    private LocalDateTime created_at;
    private LocalDateTime canceled_at;

    public enum CancelStatus {
        REQUESTED,
        PG_PENDING,
        CANCELLED,
        FAILED
    }

}
