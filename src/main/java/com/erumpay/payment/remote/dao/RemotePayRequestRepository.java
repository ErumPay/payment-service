package com.erumpay.payment.remote.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

public interface RemotePayRequestRepository extends JpaRepository<RemotePayRequestEntity, Long> {
}
