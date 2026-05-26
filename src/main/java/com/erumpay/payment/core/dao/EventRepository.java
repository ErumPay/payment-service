package com.erumpay.payment.core.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.erumpay.payment.core.domain.entity.EventEntity;

public interface EventRepository extends JpaRepository<EventEntity, Long> {
}
