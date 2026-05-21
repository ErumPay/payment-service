package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayInviteRequest {

    @NotEmpty
    private List<Long> user_ids;
}
