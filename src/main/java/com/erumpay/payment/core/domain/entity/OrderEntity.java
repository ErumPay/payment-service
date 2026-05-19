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
@Table(name = "payment_orders")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payment_id;

    // qr/request 때 저장
    private String order_no;
    private String order_name;
    private Long amount;
    private Long merchant_id;

    @Enumerated(EnumType.STRING)
    private ChannelType channel_type;

    @Enumerated(EnumType.STRING)
    private PaymentStatus payment_status;

    private LocalDateTime created_at;

    // FE 요청 id
    private String idempotency_key;

    private Long user_id;
    private PaymentType payment_type;

    // 가맹점 정보
    private String merchant_name;
    private String business_number;
    private String owner_name;
    private String contact_phone;
    private String business_address;

    // 더치 or 원격
    private Long dutch_session_id;
    private DutchRole dutch_role;
    private Long remote_request_id;

    // 결제 정보
    private FailCode fail_code;
    private LocalDateTime updated_at;
    private LocalDateTime paid_at;
    private LocalDateTime canceled_at;

    public static OrderEntity toEntity(
            String orderNo,
            String orderName,
            Long amount,
            Long merchantId,
            String channelType,
            LocalDateTime createdAt) {
        return OrderEntity.builder()
                .order_no(orderNo)
                .order_name(orderName)
                .amount(amount)
                .merchant_id(merchantId)
                .channel_type(ChannelType.valueOf(channelType.toUpperCase()))
                .payment_status(PaymentStatus.CREATED)
                .created_at(createdAt)
                .updated_at(createdAt)
                .build();
    }

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
