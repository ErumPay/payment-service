package com.erumpay.payment.qr.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.erumpay.payment.qr.domain.entity.QrEntity;

import org.springframework.data.repository.query.Param;

// @Repository
public interface QrRepository extends JpaRepository<QrEntity, Long> {
    @Query("select q from QrEntity q where q.token_hash = :token")
    Optional<QrEntity> findByToken(@Param("token") String token);

    // [be] 나영은 260529 1638 | SDK 결제 조회 응답에서 redirectUrl/qrToken을 다시 내려주기 위한 paymentId 기준 조회.
    @Query("select q from QrEntity q where q.order.paymentId = :paymentId")
    Optional<QrEntity> findByPaymentId(@Param("paymentId") Long paymentId);
}
