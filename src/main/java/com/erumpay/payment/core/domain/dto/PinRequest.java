package com.erumpay.payment.core.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PinRequest {
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "pin must be 6 digits")
    private String pin;
}
