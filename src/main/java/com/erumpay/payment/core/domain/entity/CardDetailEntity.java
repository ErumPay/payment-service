package com.erumpay.payment.core.domain.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_card_details")
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class CardDetailEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long payment_card_id;

    private Long payment_id;
    private Long pg_txn_id;
    private String pg_approval_num;
    private Long card_id;
    private String masked_number;
    private String card_name;
    private Long paid_amount;
    private Long discount_amount;
    private String benefit_desc;
    private LocalDateTime paid_at;

}
