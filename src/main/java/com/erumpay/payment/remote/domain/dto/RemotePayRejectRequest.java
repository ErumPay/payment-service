package com.erumpay.payment.remote.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class RemotePayRejectRequest {

    @Size(max = 200)
    private String reject_reason;
}
