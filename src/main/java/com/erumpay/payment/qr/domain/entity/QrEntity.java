package com.erumpay.payment.qr.domain.entity;

import java.time.LocalDateTime;

import com.erumpay.payment.core.domain.entity.OrderEntity;

import jakarta.persistence.Entity;
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
@Table(name = "payment_qr")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class QrEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long qr_id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private OrderEntity order;

    private String token_hash;

    private boolean is_used;

    @jakarta.persistence.Column(insertable = false, updatable = false)
    private Integer active;
    private LocalDateTime created_at;
    private LocalDateTime expired_at;

    public static QrEntity toEntity(
            OrderEntity order,
            String tokenHash,
            LocalDateTime createdAt,
            LocalDateTime expiredAt) {
        return QrEntity.builder()
                .order(order)
                .token_hash(tokenHash)
                .is_used(false)
                .created_at(createdAt)
                .expired_at(expiredAt)
                .build();
    }

}
