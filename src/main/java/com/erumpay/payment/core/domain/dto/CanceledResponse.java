package com.erumpay.payment.core.domain.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CanceledResponse {

    private Long paymentId;
    private String paymentStatus;
    private LocalDateTime canceledAt;
    // private List<Long> canceledPgTxnIds;

}
