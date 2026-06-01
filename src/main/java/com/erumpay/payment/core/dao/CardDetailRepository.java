package com.erumpay.payment.core.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.CardDetailEntity;

public interface CardDetailRepository extends JpaRepository<CardDetailEntity, Long> {
    @Query("select c from CardDetailEntity c where c.payment_id = :paymentId and c.pg_txn_id is not null")
    List<CardDetailEntity> findCancelableCardsByPaymentId(@Param("paymentId") Long paymentId);

    @Query("select count(c) > 0 from CardDetailEntity c where c.payment_id = :paymentId and c.pg_txn_id = :pgTxnId")
    boolean existsByPaymentIdAndPgTxnId(@Param("paymentId") Long paymentId, @Param("pgTxnId") Long pgTxnId);
}
