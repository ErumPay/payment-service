package com.erumpay.payment.core.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CardDetailEntity.CardStatus;

public interface CardDetailRepository extends JpaRepository<CardDetailEntity, Long> {
    @Query("""
            select c
            from CardDetailEntity c
            where c.payment_id = :paymentId
            order by c.paid_at desc, c.payment_card_id desc
            """)
    List<CardDetailEntity> findAllByPaymentId(@Param("paymentId") Long paymentId);

    default List<CardDetailEntity> findCancelableCardsByPaymentId(Long paymentId) {
        return findCancelableCardsByPaymentIdAndStatusNot(paymentId, CardStatus.CANCELED);
    }

    @Query("""
            select c
            from CardDetailEntity c
            where c.payment_id = :paymentId
              and c.pg_txn_id is not null
              and c.card_status <> :excludedStatus
            """)
    List<CardDetailEntity> findCancelableCardsByPaymentIdAndStatusNot(
            @Param("paymentId") Long paymentId,
            @Param("excludedStatus") CardStatus excludedStatus);

    @Query("select count(c) > 0 from CardDetailEntity c where c.payment_id = :paymentId and c.pg_txn_id = :pgTxnId")
    boolean existsByPaymentIdAndPgTxnId(@Param("paymentId") Long paymentId, @Param("pgTxnId") Long pgTxnId);
}
