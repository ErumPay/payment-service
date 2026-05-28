package com.erumpay.payment.remote.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RemotePayCoreCreateRequest {

    @NotNull
    @Positive
    private Long payment_id;

    @NotNull
    @Positive
    private Long target_user_id;

    @Size(max = 200)
    private String description;
}
