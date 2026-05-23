package com.erumpay.payment.dutch.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;

public interface DutchPayParticipantRepository extends JpaRepository<DutchPayParticipantEntity, Long> {

    // [be] 영은 260523 1120 | core 참여자 결제 검증 시 session/participant/user 조합이 맞는지 조회한다
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

    // [be] 영은 260523 1120 | 세션 안에서 특정 회원의 참여자 row를 찾는다
    @Query("""
            select p
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
              and p.user_id = :userId
            """)
    Optional<DutchPayParticipantEntity> findBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    // [be] 영은 260523 1120 | 중복 초대와 중복 링크 수락을 막기 위해 참여 여부만 확인한다
    @Query("""
            select count(p) > 0
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
              and p.user_id = :userId
            """)
    boolean existsBySessionIdAndUserId(
            @Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    // [be] 영은 260523 1120 | 세션 상세 응답과 배분 계산을 위해 참여자 목록을 고정 순서로 조회한다
    @Query("""
            select p
            from DutchPayParticipantEntity p
            where p.session.session_id = :sessionId
            order by p.participant_id
            """)
    List<DutchPayParticipantEntity> findBySessionIdOrderByParticipantId(@Param("sessionId") Long sessionId);
}
