package com.erumpay.payment.core.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CardDetailEntity.CardStatus;
import com.erumpay.payment.core.domain.entity.CoreEntity;

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

    @Query("select c from CardDetailEntity c where c.payment_id = :paymentId and c.pg_txn_id = :pgTxnId")
    Optional<CardDetailEntity> findByPaymentIdAndPgTxnId(
            @Param("paymentId") Long paymentId,
            @Param("pgTxnId") Long pgTxnId);

    @Query("select count(c) > 0 from CardDetailEntity c where c.payment_id = :paymentId and c.pg_txn_id = :pgTxnId")
    boolean existsByPaymentIdAndPgTxnId(@Param("paymentId") Long paymentId, @Param("pgTxnId") Long pgTxnId);

    @Query(value = """
            select coalesce(o.merchant_name, o.order_name) as merchantName,
                   c.paid_at as paidAt,
                   c.paid_amount as amount,
                   c.card_status as cardStatus,
                   lc.cancel_status as cancelStatus
            from payment_card_details c
            join payment_orders o
              on o.payment_id = c.payment_id
            left join (
                select history.payment_id,
                       history.pg_txn_id,
                       history.cancel_status
                from (
                    select h.payment_id,
                           h.pg_txn_id,
                           h.cancel_status,
                           row_number() over (
                               partition by h.payment_id, h.pg_txn_id
                               order by coalesce(h.canceled_at, h.created_at) desc, h.cancel_id desc
                           ) as rn
                    from payment_cancel_history h
                ) history
                where history.rn = 1
            ) lc
              on lc.payment_id = c.payment_id
             and lc.pg_txn_id = c.pg_txn_id
            where o.user_id = :userId
              and c.card_id = :cardId
            order by c.paid_at desc, c.payment_card_id desc
            """, nativeQuery = true)
    List<CardPaymentHistoryProjection> findCardPaymentHistories(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId);

    @Query("""
            select c.card_id as cardId,
                   count(distinct o.paymentId) as paymentCount,
                   coalesce(sum(c.paid_amount), 0) as paidAmount
            from CardDetailEntity c
            join CoreEntity o on o.paymentId = c.payment_id
            where o.userId = :userId
              and o.payment_status = :paymentStatus
              and o.paidAt >= :from
              and o.paidAt < :to
              and c.card_status = :cardStatus
            group by c.card_id
            order by paidAmount desc, paymentCount desc
            """)
    List<CardUsageProjection> findCardUsages(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("paymentStatus") CoreEntity.PaymentStatus paymentStatus,
            @Param("cardStatus") CardStatus cardStatus);

    interface CardUsageProjection {
        Long getCardId();

        Long getPaymentCount();

        Long getPaidAmount();
    }

    interface CardPaymentHistoryProjection {
        String getMerchantName();

        LocalDateTime getPaidAt();

        Long getAmount();

        String getCardStatus();

        String getCancelStatus();
    }
}
