package com.erumpay.payment.dutch.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;

public interface DutchPayParticipantRepository extends JpaRepository<DutchPayParticipantEntity, Long> {
}
