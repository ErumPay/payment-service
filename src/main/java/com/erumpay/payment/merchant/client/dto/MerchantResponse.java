package com.erumpay.payment.merchant.client.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {
    Long merchantId;
    String merchantName;
    String businessNumber;
    String ownerName;
    String contactPhone;
    String businessAddress;
    String categoryName;
    String mccCode;
    String apiKey;
    // ApiKeyStatus apiKeyStatus;
    LocalDateTime apiKeyIssuedAt;
    LocalDateTime apiKeyRotatedAt;
    BigDecimal feeRate;
    String settlementAccount;
    // MerchantStatus status;
    String suspendReason;
    LocalDateTime updatedAt;
    LocalDateTime deletedAt;
    LocalDateTime createdAt;

}
