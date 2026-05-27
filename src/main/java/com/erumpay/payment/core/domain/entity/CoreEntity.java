package com.erumpay.payment.core.domain.entity;

import java.time.LocalDateTime;
import java.util.Locale;

import jakarta.persistence.Column;
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
public class CoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

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
    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    private PaymentType payment_type;

    // 가맹점 정보
    private String merchant_name;
    private String business_number;
    private String owner_name;
    private String contact_phone;
    private String business_address;

    // 더치
    private Long dutch_session_id;
    @Enumerated(EnumType.STRING)
    private DutchRole dutch_role;

    // 원격
    private Long remote_request_id;

    // 결제 정보
    @Enumerated(EnumType.STRING)
    private FailCode fail_code;
    private LocalDateTime updated_at;
    private LocalDateTime paid_at;
    private LocalDateTime canceled_at;

    // [be] 다윤 260526 결제 요청 status 업데이트
    public void preparePayment(
            String idempotencyKey,
            Long userId,
            PaymentType paymentType,
            LocalDateTime updatedAt) {
        this.idempotencyKey = idempotencyKey;
        this.userId = userId;
        this.payment_type = paymentType;
        this.payment_status = PaymentStatus.PAY_PENDING;
        this.updated_at = updatedAt;
    }

    // [be] 다윤 260526 대표자 세션아이디 업데이트
    public void hostDutchSessionPayment(
            Long dutch_session_id, DutchRole dutch_role) {
        this.dutch_session_id = dutch_session_id;
        this.dutch_role = dutch_role;
        this.updated_at = LocalDateTime.now();
    }

    // [be] 다윤 260526 실결제 status 업데이트
    public void pgRequestUpdateStatusPayment(LocalDateTime updatedAt) {
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt must not be null");
        }
        this.payment_status = PaymentStatus.PG_PENDING;
        this.updated_at = updatedAt;
    }

    // [be] 다윤 260526 실결제 성공 status 업데이트
    public void paidStatusUpdatePayment(LocalDateTime paidAt) {
        if (paidAt == null) {
            throw new IllegalArgumentException("paidAt must not be null");
        }
        this.payment_status = PaymentStatus.PAID;
        this.paid_at = paidAt;
        this.updated_at = paidAt;
    }

    // [be] 다윤 260527 실결제 실패 status 업데이트
    public void failedStatusUpdatePayment(LocalDateTime failedAt) {
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt must not be null");
        }
        this.payment_status = PaymentStatus.FAILED;
        this.updated_at = failedAt;
    }

    // [be] 다윤 260527 취소 성공 status 업데이트
    public void voidedStatusUpdatePayment(LocalDateTime canceledAt) {
        if (canceledAt == null) {
            throw new IllegalArgumentException("canceledAt must not be null");
        }
        this.payment_status = PaymentStatus.CANCELED;
        this.canceled_at = canceledAt;
        this.updated_at = canceledAt;
    }

    // [be] 다윤 260526 QR 생성시 new entity 생성
    public static CoreEntity toEntity(
            String orderNo,
            String orderName,
            Long amount,
            Long merchantId,
            String channelType,
            LocalDateTime createdAt) {
        return CoreEntity.builder()
                .order_no(orderNo)
                .order_name(orderName)
                .amount(amount)
                .merchant_id(merchantId)
                .channel_type(ChannelType.valueOf(channelType.trim().toUpperCase(Locale.ROOT)))
                .payment_status(PaymentStatus.CREATED)
                .created_at(createdAt)
                .updated_at(createdAt)
                .build();
    }

    public static CoreEntity toDutchHostAuthEntity(
            String orderNo,
            String orderName,
            Long amount,
            Long userId,
            Long merchantId,
            String idempotencyKey,
            LocalDateTime createdAt) {
        // [be] 영은 260519 1440 | 대표자 가승인은 실제 매입 전 AUTHORIZED 상태의 더치페이 주문으로 기록
        return CoreEntity.builder()
                .order_no(orderNo)
                .order_name(orderName)
                .amount(amount)
                .userId(userId)
                .merchant_id(merchantId)
                .idempotencyKey(idempotencyKey)
                .channel_type(ChannelType.OFFLINE)
                .payment_type(PaymentType.DUTCH)
                .dutch_role(DutchRole.HOST)
                .payment_status(PaymentStatus.AUTHORIZED)
                .created_at(createdAt)
                .updated_at(createdAt)
                .build();
    }

    public static CoreEntity toRemotePaymentEntity(
            String orderNo,
            String orderName,
            Long amount,
            Long userId,
            Long remoteRequestId,
            String idempotencyKey,
            LocalDateTime createdAt) {
        return CoreEntity.builder()
                .order_no(orderNo)
                .order_name(orderName)
                .amount(amount)
                .userId(userId)
                .idempotencyKey(idempotencyKey)
                .channel_type(ChannelType.ONLINE)
                .payment_type(PaymentType.REMOTE)
                .remote_request_id(remoteRequestId)
                .payment_status(PaymentStatus.PAY_PENDING)
                .created_at(createdAt)
                .updated_at(createdAt)
                .build();
    }

    public void connectDutchSession(Long dutchSessionId) {
        if (dutchSessionId == null) {
            throw new IllegalArgumentException("dutchSessionId must not be null");
        }
        if (this.dutch_session_id != null && !this.dutch_session_id.equals(dutchSessionId)) {
            throw new IllegalStateException("Order is already connected to another dutch session");
        }

        this.dutch_session_id = dutchSessionId;
        this.updated_at = LocalDateTime.now();
    }

    public enum PaymentStatus {
        CREATED,
        PAY_PENDING,
        PG_PENDING,
        PAID,
        FAILED,
        EXPIRED,
        AUTHORIZED,
        VOIDED,
        CANCELED
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
