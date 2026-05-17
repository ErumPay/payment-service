package com.erumpay.payment.qr.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.erumpay.payment.qr.domain.entity.QrEntity;

public interface QrRepository extends JpaRepository<QrEntity, Long> {
}
