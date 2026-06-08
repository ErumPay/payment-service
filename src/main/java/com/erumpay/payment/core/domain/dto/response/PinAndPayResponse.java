package com.erumpay.payment.core.domain.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "비밀번호 확인 후 결제 응답")
public class PinAndPayResponse {

    @Schema(description = "결제 ID", example = "10001")
    private Long paymentId;
    @Schema(description = "요청 사용자 ID", example = "1")
    private Long userId;
    @Schema(description = "결제 상태", example = "PAID")
    private String paymentStatus;
    @Schema(description = "결제 타입", example = "SINGLE")
    private String paymentType;

}
