package com.erumpay.payment.core.dao;

import java.util.Optional;

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
}
