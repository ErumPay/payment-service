package com.erumpay.payment.core.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.EventEntity;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
    @Query("""
            select e
            from EventEntity e
            where e.payment_id = :paymentId
              and e.event_type = :eventType
              and e.pg_txn_id is not null
            order by e.created_at desc
            """)
    List<EventEntity> findPgTxnEventsByPaymentIdAndEventType(
            @Param("paymentId") Long paymentId,
            @Param("eventType") EventEntity.EventType eventType,
            Pageable pageable);
}
