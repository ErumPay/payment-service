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

    // 404
    QR_NOT_FOUND(HttpStatus.NOT_FOUND, "QR 토큰 정보를 찾을 수 없습니다."),

    // 410
    QR_EXPIRED(HttpStatus.GONE, "QR 토큰이 만료되었습니다."),
    QR_USED(HttpStatus.GONE, "이미 사용된 QR 토큰입니다."),

    // 500
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 에러가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

}
