package com.erumpay.payment.core.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    QR_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 토큰입니다."),
    PIN_INVALID(HttpStatus.BAD_REQUEST, "잘못된 비밀번호입니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "잘못된 금액 요청입니다."),
    DUPLICATED_REQUEST(HttpStatus.BAD_REQUEST, "중복된 요청입니다."),
    CANCELED_INVALID(HttpStatus.BAD_REQUEST, "취소 불가능한 결제건입니다."),
    CANCELED_CARD_INVALID(HttpStatus.BAD_REQUEST, "취소 가능한 카드가 없습니다."),
    CANCELED_PG_REJECTED(HttpStatus.BAD_REQUEST, "취소가 거절되었습니다."),
    CARD_BILLING_KEY_INVALID(HttpStatus.BAD_REQUEST, "유효하지 않은 카드/빌링키 요청입니다."),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CARD_BILLING_KEY_FORBIDDEN(HttpStatus.FORBIDDEN, "카드 빌링키 접근 권한이 없습니다."),

    // 404
    QR_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 토큰 정보를 찾을 수 없습니다."),
    PAY_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 정보를 찾을 수 없습니다."),
    CARD_BILLING_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "카드 또는 빌링키 정보를 찾을 수 없습니다."),

    // 409
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "요청 처리 중입니다."),

    // 410
    QR_EXPIRED(HttpStatus.GONE, "QR 토큰이 만료되었습니다."),
    QR_USED(HttpStatus.GONE, "이미 사용된 QR 토큰입니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 에러가 발생했습니다."),
    INTERNAL_PG_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 내부 에러가 발생했습니다."),
    INTERNAL_CARD_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "카드 서버 내부 에러가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

}
