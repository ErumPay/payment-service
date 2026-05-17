package com.erumpay.payment.core.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erumpay.payment.core.domain.entity.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    @Query("select count(o) > 0 from OrderEntity o where o.order_no = :orderNo")
    boolean existsByOrderNo(@Param("orderNo") String orderNo);
}
