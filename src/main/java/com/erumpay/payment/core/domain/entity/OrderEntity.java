package com.erumpay.payment.core.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_orders")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payment_id;

    @Column(unique = true)
    private String order_no;
    private String order_name;
    private Long amount;

    private PaymentStatus payment_status;

    @Column(unique = true)
    private String idempotency_key;

    private Long user_id;
    private Long merchant_id;
    private String merchant_name;
    private String business_number;
    private String owner_name;
    private String contact_phone;
    private String business_address;
    private ChannelType channel_type;
    private PaymentType payment_type;
    private Long dutch_session_id;
    private DutchRole dutch_role;
    private Long remote_request_id;
    private LocalDateTime updated_at;
    private FailCode fail_code;
    private LocalDateTime created_at;
    private LocalDateTime paid_at;
    private LocalDateTime canceled_at;

    public enum PaymentStatus {
        CREATED,
        PAY_PENDING,
        PG_PENDING,
        PAID,
        FAILED,
        EXPIRED,
        AUTHORIZED,
        VOIDED
    }

    public enum ChannelType {
        ONLINE,
        OFFLINE
    }

    public enum PaymentType {
        SINGLE,
        DUTCH,
        REMOTE
    }

    public enum DutchRole {
        HOST,
        MEMBER
    }

    public enum FailCode {
        CARD_EXPIRED,
        INVALID_PIN,
        QR_EXPIRED,
        NETWORK_ERROR,
        DUPLICATE_PAYMENT
    }

}
