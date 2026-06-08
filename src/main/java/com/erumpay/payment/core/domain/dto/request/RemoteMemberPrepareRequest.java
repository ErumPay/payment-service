package com.erumpay.payment.core.domain.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

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
public class RemoteMemberPrepareRequest {

    @NotNull
    @Positive
    @Schema(description = "결제 금액", example = "5000")
    private Long amount;

    @NotNull
    @Positive
    @Schema(description = "원격결제 ID", example = "1")
    private Long remoteRequestId;
}
