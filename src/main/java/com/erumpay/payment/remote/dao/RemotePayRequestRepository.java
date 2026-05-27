package com.erumpay.payment.remote.dao;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity.RemotePayStatus;

public interface RemotePayRequestRepository extends JpaRepository<RemotePayRequestEntity, Long> {

    // [be] 영은 260527 1030 | 요청 상태 변경 시 같은 request_id를 직렬화해 중복 완료/취소/거절 전이를 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RemotePayRequestEntity r where r.request_id = :requestId")
    Optional<RemotePayRequestEntity> findByIdForUpdate(@Param("requestId") Long requestId);

    // [be] 영은 260527 1010 | 상세 조회에서 payment_id까지 보여주기 위해 payment_orders를 함께 조회한다.
    @Query("""
            select r
            from RemotePayRequestEntity r
            left join fetch r.payment
            where r.request_id = :requestId
            """)
    Optional<RemotePayRequestEntity> findDetailById(@Param("requestId") Long requestId);

    // [be] 영은 260527 1020 | 요청자/대상자 양쪽 홈 화면에서 진행 중 원격결제 요청을 조회한다.
    @Query("""
            select r
            from RemotePayRequestEntity r
            left join fetch r.payment
            where (r.requester_user_id = :userId or r.target_user_id = :userId)
              and r.status in :statuses
            order by r.created_at desc
            """)
    List<RemotePayRequestEntity> findRequestsByUserIdAndStatuses(
            @Param("userId") Long userId,
            @Param("statuses") List<RemotePayStatus> statuses);
}
