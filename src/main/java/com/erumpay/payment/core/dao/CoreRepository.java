package com.erumpay.payment.core.dao;

import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.CoreEntity;

public interface CoreRepository extends JpaRepository<CoreEntity, Long> {
    @Query("select count(o) > 0 from CoreEntity o where o.order_no = :orderNo")
    boolean existsByOrderNo(@Param("orderNo") String orderNo);

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

    Slice<CoreEntity> findAllByUserId(Long userId, Pageable pageable);
}
