package com.erumpay.payment.core.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.CoreEntity;

import jakarta.persistence.LockModeType;

public interface CoreRepository extends JpaRepository<CoreEntity, Long> {
        @Query("select count(o) > 0 from CoreEntity o where o.order_no = :orderNo")
        boolean existsByOrderNo(@Param("orderNo") String orderNo);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select o from CoreEntity o where o.paymentId = :paymentId")
        Optional<CoreEntity> findByIdForUpdate(@Param("paymentId") Long paymentId);

        boolean existsByIdempotencyKey(String idempotencyKey);

        Optional<CoreEntity> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

        boolean existsByPaymentIdAndUserId(Long paymentId, Long userId);

        // [be] 나영은 260529 1638 | SDK 가맹점 결제 생성 멱등성 확인용 조회. 기존 userId 기반 멱등성 흐름과 분리한다.
        @Query("select o from CoreEntity o where o.merchant_id = :merchantId and o.idempotencyKey = :idempotencyKey")
        Optional<CoreEntity> findByMerchantIdAndIdempotencyKey(
                        @Param("merchantId") Long merchantId,
                        @Param("idempotencyKey") String idempotencyKey);

        // [be] 나영은 260529 1638 | SDK 가맹점 결제 조회/취소 시 paymentId가 해당 merchant 소유인지 확인한다.
        @Query("select o from CoreEntity o where o.paymentId = :paymentId and o.merchant_id = :merchantId")
        Optional<CoreEntity> findByPaymentIdAndMerchantId(
                        @Param("paymentId") Long paymentId,
                        @Param("merchantId") Long merchantId);

        // [be] 다윤 260602 13:00 | 결제 내역 조회
        @Query("""
                        select o
                        from CoreEntity o
                        where o.userId = :userId
                          and o.payment_status in :paymentStatuses
                          and (:from is null or o.paidAt >= :from)
                          and (:to is null or o.paidAt < :to)
                          and (:paymentType is null or o.payment_type = :paymentType)
                          and (:strategyType is null or o.strategy_type = :strategyType)
                        """)
        Slice<CoreEntity> findAllByUserIdAndPaymentStatuses(
                        @Param("userId") Long userId,
                        @Param("paymentStatuses") java.util.List<CoreEntity.PaymentStatus> paymentStatuses,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("paymentType") CoreEntity.PaymentType paymentType,
                        @Param("strategyType") CoreEntity.StrategyType strategyType,
                        Pageable pageable);

        @Query("""
                        select coalesce(sum(o.amount), 0) as totalAmount,
                               count(o) as paymentCount
                        from CoreEntity o
                        where o.userId = :userId
                          and o.payment_status = :paymentStatus
                          and o.paidAt >= :from
                          and o.paidAt < :to
                        """)
        PaymentUsageTotalProjection findPaymentUsageTotal(
                        @Param("userId") Long userId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("paymentStatus") CoreEntity.PaymentStatus paymentStatus);

        @Query("""
                        select coalesce(o.merchant_name, o.order_name) as merchantName,
                               count(o) as paymentCount,
                               coalesce(sum(o.amount), 0) as paidAmount
                        from CoreEntity o
                        where o.userId = :userId
                          and o.payment_status = :paymentStatus
                          and o.paidAt >= :from
                          and o.paidAt < :to
                        group by coalesce(o.merchant_name, o.order_name)
                        order by paidAmount desc, paymentCount desc
                        """)
        List<MerchantUsageProjection> findMerchantUsages(
                        @Param("userId") Long userId,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("paymentStatus") CoreEntity.PaymentStatus paymentStatus);

        interface PaymentUsageTotalProjection {
                Long getTotalAmount();

                Long getPaymentCount();
        }

        interface MerchantUsageProjection {
                String getMerchantName();

                Long getPaymentCount();

                Long getPaidAmount();
        }

        // [be] 다윤 260605 20:00 | 사용자 회원 탈퇴 차단 결제건 조회
        @Query("""
                        select count(o)
                        from CoreEntity o
                        where o.userId = :userId
                          and o.payment_status in :paymentStatuses
                        """)
        long countByUserIdAndPaymentStatuses(
                        @Param("userId") Long userId,
                        @Param("paymentStatuses") List<CoreEntity.PaymentStatus> paymentStatuses);
}
