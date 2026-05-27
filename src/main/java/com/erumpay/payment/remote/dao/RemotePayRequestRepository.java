package com.erumpay.payment.remote.dao;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

public interface RemotePayRequestRepository extends JpaRepository<RemotePayRequestEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RemotePayRequestEntity r where r.request_id = :requestId")
    Optional<RemotePayRequestEntity> findByIdForUpdate(@Param("requestId") Long requestId);
}
