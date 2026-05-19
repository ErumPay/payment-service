package com.erumpay.payment.qr.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.erumpay.payment.qr.domain.entity.QrEntity;

import org.springframework.data.repository.query.Param;

public interface QrRepository extends JpaRepository<QrEntity, Long> {
    @Query("select q from QrEntity q where q.token_hash = :token")
    Optional<QrEntity> findByToken(@Param("token") String token);
}
