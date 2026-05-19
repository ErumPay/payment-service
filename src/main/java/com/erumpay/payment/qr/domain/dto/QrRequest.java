package com.erumpay.payment.qr.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class QrRequest {
    private Long merchant_id;

    @NotNull
    private Long amount;

    @NotBlank
    private String order_name;

    @NotBlank
    private String channel_type;
}
