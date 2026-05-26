package com.erumpay.payment.dutch.dao;

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
            order by s.created_at desc
            """)
    List<DutchPaySessionEntity> findActiveSessionsByUserId(
            @Param("userId") Long userId,
            @Param("statuses") List<DutchPayStatus> statuses);

    // Locks one session while deciding whether participant payment callbacks can complete it.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DutchPaySessionEntity s where s.session_id = :sessionId")
    Optional<DutchPaySessionEntity> findByIdForUpdate(@Param("sessionId") Long sessionId);
}
