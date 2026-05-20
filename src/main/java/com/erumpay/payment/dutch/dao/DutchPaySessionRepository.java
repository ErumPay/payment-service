package com.erumpay.payment.dutch.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;

public interface DutchPaySessionRepository extends JpaRepository<DutchPaySessionEntity, Long> {

    @Query("select count(s) > 0 from DutchPaySessionEntity s where s.dutch_order_no = :dutchOrderNo")
    boolean existsByDutchOrderNo(@Param("dutchOrderNo") String dutchOrderNo);
}
