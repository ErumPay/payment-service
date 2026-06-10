package com.erumpay.payment.dutch.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;

public interface DutchPaySessionRepository extends JpaRepository<DutchPaySessionEntity, Long> {

    // [be] 영은 260523 1120 | 더치페이 주문번호 생성 시 중복 여부를 확인한다
    @Query("select count(s) > 0 from DutchPaySessionEntity s where s.dutch_order_no = :dutchOrderNo")
    boolean existsByDutchOrderNo(@Param("dutchOrderNo") String dutchOrderNo);

    // [be] 영은 260523 1120 | 홈 화면 이어하기용으로 대표자/참여자에 해당하는 진행 중 세션을 조회한다
    @Query("""
            select distinct s
            from DutchPaySessionEntity s
            left join DutchPayParticipantEntity p on p.session = s
            where s.status in :statuses
              and (s.host_user_id = :userId or p.user_id = :userId)
              and (s.status <> :inProgressStatus or s.created_at > :timeoutThreshold)
            order by s.created_at desc
            """)
    List<DutchPaySessionEntity> findActiveSessionsByUserId(
            @Param("userId") Long userId,
            @Param("statuses") List<DutchPayStatus> statuses,
            @Param("inProgressStatus") DutchPayStatus inProgressStatus,
            @Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    // Locks one session while deciding whether participant payment callbacks can complete it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DutchPaySessionEntity s where s.session_id = :sessionId")
    Optional<DutchPaySessionEntity> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from DutchPaySessionEntity s
            where s.status = :status
              and s.timeout_at is null
              and s.created_at <= :timeoutThreshold
            order by s.created_at asc
            """)
    List<DutchPaySessionEntity> findTimeoutTargetsForUpdate(
            @Param("status") DutchPayStatus status,
            @Param("timeoutThreshold") LocalDateTime timeoutThreshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from DutchPaySessionEntity s
            where s.status = :status
              and s.timeout_at is null
              and s.warning_1_sent_at is null
              and s.created_at <= :warningThreshold
            order by s.created_at asc
            """)
    List<DutchPaySessionEntity> findWarning1TargetsForUpdate(
            @Param("status") DutchPayStatus status,
            @Param("warningThreshold") LocalDateTime warningThreshold);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from DutchPaySessionEntity s
            where s.status = :status
              and s.timeout_at is null
              and s.warning_1_sent_at is not null
              and s.warning_2_sent_at is null
              and s.created_at <= :warningThreshold
            order by s.created_at asc
            """)
    List<DutchPaySessionEntity> findWarning2TargetsForUpdate(
            @Param("status") DutchPayStatus status,
            @Param("warningThreshold") LocalDateTime warningThreshold);
}
