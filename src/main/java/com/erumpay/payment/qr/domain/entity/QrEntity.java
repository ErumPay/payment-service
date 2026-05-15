package com.erumpay.payment.qr.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
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
    private Long qr_id;
    private Long payment_id;
    private String token_hash;
    private boolean is_used;
    private Integer active;
    private LocalDateTime created_at;
    private LocalDateTime expired_at;

}
