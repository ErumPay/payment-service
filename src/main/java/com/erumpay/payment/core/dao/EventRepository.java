package com.erumpay.payment.core.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.EventEntity;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
    // [be] 다윤 260607 05:00 | 결제 상태 이벤트 중복 여부 조회 메서드
    @Query("""
            select count(e) > 0
            from EventEntity e
            where e.payment_id = :paymentId
              and e.event_type = :eventType
            """)
    boolean existsByPaymentIdAndEventType(
            @Param("paymentId") Long paymentId,
            @Param("eventType") EventEntity.EventType eventType);

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
