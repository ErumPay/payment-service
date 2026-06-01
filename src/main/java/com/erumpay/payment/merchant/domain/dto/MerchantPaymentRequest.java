package com.erumpay.payment.merchant.domain.dto;

import com.erumpay.payment.core.domain.entity.CoreEntity;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
// [be] 나영은 260529 1638 | 오픈 SDK 결제 생성 요청 DTO. 외부 가맹점에는 camelCase JSON 계약을 제공한다.
public class MerchantPaymentRequest {

    @NotNull
    @Positive
    private Long amount;

    @NotBlank
    private String orderName;

    @NotNull
    private CoreEntity.ChannelType channel;

    @URL
    private String successUrl;
    @URL
    private String failUrl;
}
