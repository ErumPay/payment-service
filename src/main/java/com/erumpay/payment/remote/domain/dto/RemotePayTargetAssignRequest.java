package com.erumpay.payment.remote.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "원격결제 draft 요청에 대리결제자를 지정하는 요청")
public class RemotePayTargetAssignRequest {

    @NotNull
    @Positive
    @Schema(description = "실제로 결제를 대신 수행할 사용자 ID", example = "3")
    private Long target_user_id;
}
