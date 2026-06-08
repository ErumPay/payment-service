package com.erumpay.payment.core.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.erumpay.payment.core.domain.entity.CancelEntity;

// [be] 다윤 260607 05:00 | 결제 취소 이력 저장 repository
public interface CancelRepository extends JpaRepository<CancelEntity, Long> {
}
