package com.erumpay.payment.core.domain.dto.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class PaymentDetailResponse {

    private Long userId;
    private Long paymentId;

    private String paymentType;
    private String strategyType;
    private String status;
    // [be] 영은 260610 | 원격결제 결제내역 상세 화면에서 요청자/대리결제자 역할과 연결 요청을 식별한다.
    private Long remoteRequestId;
    private Long requesterUserId;
    private Long targetUserId;
    private String remoteRole;

    private Long amount;
    private String orderName;
    private String orderNo;
    private LocalDateTime paidAt;
    private LocalDateTime canceledAt;
    private List<CardItem> cards;

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
    public static class CardItem {
        private Long paymentCardId;
        private Long cardId;
        private String cardName;
        private String maskedNumber;
        private Long paidAmount;
        private Long discountAmount;
        private String benefitDesc;
        private String cardStatus;
        private LocalDateTime canceledAt;

    }

}
