package com.erumpay.payment.dutch.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;

public interface DutchPayParticipantRepository extends JpaRepository<DutchPayParticipantEntity, Long> {

    @Query("""
            select p
            from DutchPayParticipantEntity p
            where p.participant_id = :participantId
              and p.session.session_id = :sessionId
              and p.user_id = :userId
            """)
    Optional<DutchPayParticipantEntity> findParticipantForPaymentValidation(
            @Param("sessionId") Long sessionId,
            @Param("participantId") Long participantId,
            @Param("userId") Long userId);

    @Query("""
            select p
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
              and p.user_id = :userId
            """)
    Optional<DutchPayParticipantEntity> findBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Query("""
            select count(p) > 0
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
              and p.user_id = :userId
            """)
    boolean existsBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Query("""
            select p
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
            order by p.participant_id
            """)
    List<DutchPayParticipantEntity> findBySessionIdOrderByParticipantId(@Param("sessionId") Long sessionId);
}
