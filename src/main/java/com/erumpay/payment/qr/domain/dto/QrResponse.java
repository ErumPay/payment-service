package com.erumpay.payment.qr.domain.dto;

import com.erumpay.payment.core.domain.entity.CoreEntity;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "QR 검증 응답")
public class QrResponse {

    @Schema(description = "결제 ID", example = "10001")
    private Long paymentId;
    @Schema(description = "QR 상태 코드", example = "VALID")
    private String code;
    @Schema(description = "결제 금액", example = "12000")
    private Long amount;
    @Schema(description = "가맹점명", example = "스타벅스 강남점")
    private String merchant_name;
    @Schema(description = "채널 타입", example = "ONLINE")
    private String channel_type;

    public static QrResponse fromOrderEntity(CoreEntity entity, String code) {
        return QrResponse.builder()
                .code(code)
                .paymentId(entity.getPaymentId())
                .amount(entity.getAmount())
                .merchant_name(resolveMerchantName(entity))
                .channel_type(entity.getChannel_type().name())
                .build();
    }

    private static String resolveMerchantName(CoreEntity entity) {
        return entity.getMerchant_name();
    }
}
