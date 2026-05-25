package com.erumpay.payment.dutch.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;

public interface DutchPaySessionRepository extends JpaRepository<DutchPaySessionEntity, Long> {

    @Query("select count(s) > 0 from DutchPaySessionEntity s where s.dutch_order_no = :dutchOrderNo")
    boolean existsByDutchOrderNo(@Param("dutchOrderNo") String dutchOrderNo);

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
}
