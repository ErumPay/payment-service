package com.erumpay.payment.core.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Core / QR / authentication
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "PAY-CORE-001", "CORE_INVALID_REQUEST", "잘못된 요청입니다."),
    QR_INVALID(HttpStatus.BAD_REQUEST, "PAY-QR-001", "QR_INVALID_TOKEN", "유효하지 않은 QR 토큰입니다."),
    PIN_INVALID(HttpStatus.BAD_REQUEST, "PAY-AUTH-001", "PIN_INVALID", "잘못된 비밀번호입니다."),
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAY-CORE-002", "PAYMENT_AMOUNT_MISMATCH", "잘못된 금액 요청입니다."),
    CANCELED_INVALID(HttpStatus.BAD_REQUEST, "PAY-CORE-204", "PAYMENT_CANCEL_NOT_ALLOWED", "취소 불가능한 결제건입니다."),
    CANCELED_CARD_INVALID(HttpStatus.BAD_REQUEST, "PAY-CORE-205", "PAYMENT_CANCEL_CARD_NOT_FOUND", "취소 가능한 카드가 없습니다."),
    CANCELED_PG_REJECTED(HttpStatus.BAD_REQUEST, "PAY-PG-402", "PG_CANCEL_REJECTED", "취소가 거절되었습니다."),
    CARD_BILLING_KEY_INVALID(HttpStatus.BAD_REQUEST, "PAY-CARD-001", "CARD_BILLING_KEY_INVALID", "유효하지 않은 카드/빌링키 요청입니다."),

    // Authorization / ownership
    FORBIDDEN(HttpStatus.FORBIDDEN, "PAY-CORE-101", "PAYMENT_ACCESS_DENIED", "접근 권한이 없습니다."),
    CARD_BILLING_KEY_FORBIDDEN(HttpStatus.FORBIDDEN, "PAY-CARD-101", "CARD_BILLING_KEY_FORBIDDEN", "카드 빌링키 접근 권한이 없습니다."),
    MERCHANT_API_KEY_MISSING(HttpStatus.UNAUTHORIZED, "PAY-MER-101", "MERCHANT_API_KEY_MISSING", "API key가 필요합니다."),
    MERCHANT_API_KEY_INVALID(HttpStatus.UNAUTHORIZED, "PAY-MER-102", "MERCHANT_API_KEY_INVALID", "API key가 올바르지 않습니다."),

    // Not found
    QR_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-QR-201", "QR_NOT_FOUND", "QR 토큰 정보를 찾을 수 없습니다."),
    PAY_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-CORE-201", "PAYMENT_NOT_FOUND", "결제 정보를 찾을 수 없습니다."),
    CARD_BILLING_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-CARD-201", "CARD_BILLING_KEY_NOT_FOUND", "카드 또는 빌링키 정보를 찾을 수 없습니다."),

    // Conflict / idempotency
    DUPLICATED_REQUEST(HttpStatus.CONFLICT, "PAY-CORE-301", "PAYMENT_IDEMPOTENCY_CONFLICT", "중복된 요청입니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "PAY-CORE-203", "PAYMENT_REQUEST_IN_PROGRESS", "요청 처리 중입니다."),

    // Expired / used
    QR_EXPIRED(HttpStatus.GONE, "PAY-QR-202", "QR_EXPIRED", "QR 토큰이 만료되었습니다."),
    QR_USED(HttpStatus.GONE, "PAY-QR-203", "QR_USED", "이미 사용된 QR 토큰입니다."),

    // DutchPay
    DUTCH_INVALID_REQUEST(HttpStatus.BAD_REQUEST, "PAY-DUT-001", "DUTCH_INVALID_REQUEST", "잘못된 더치페이 요청입니다."),
    DUTCH_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "PAY-DUT-002", "DUTCH_INVALID_AMOUNT", "더치페이 금액이 올바르지 않습니다."),
    DUTCH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PAY-DUT-101", "DUTCH_ACCESS_DENIED", "더치페이에 접근할 권한이 없습니다."),
    DUTCH_HOST_ONLY_ACTION(HttpStatus.FORBIDDEN, "PAY-DUT-102", "DUTCH_HOST_ONLY_ACTION", "대표자만 수행할 수 있는 요청입니다."),
    DUTCH_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-DUT-201", "DUTCH_SESSION_NOT_FOUND", "더치페이 세션을 찾을 수 없습니다."),
    DUTCH_PARTICIPANT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY-DUT-202", "DUTCH_PARTICIPANT_NOT_FOUND", "더치페이 참여자를 찾을 수 없습니다."),
    DUTCH_SESSION_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "PAY-DUT-203", "DUTCH_SESSION_NOT_IN_PROGRESS", "더치페이 세션이 진행 중 상태가 아닙니다."),
    DUTCH_SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "PAY-DUT-204", "DUTCH_SESSION_ALREADY_COMPLETED", "이미 완료된 더치페이 세션입니다."),
    DUTCH_SESSION_FAILED(HttpStatus.CONFLICT, "PAY-DUT-205", "DUTCH_SESSION_FAILED", "더치페이 세션이 실패 상태입니다."),
    DUTCH_SESSION_TIMEOUT_HANDLED(HttpStatus.CONFLICT, "PAY-DUT-206", "DUTCH_SESSION_TIMEOUT_HANDLED", "더치페이 세션이 타임아웃 처리되었습니다."),
    DUTCH_HOST_AUTH_NOT_CREATED(HttpStatus.CONFLICT, "PAY-DUT-207", "DUTCH_HOST_AUTH_NOT_CREATED", "대표자 가승인 결제가 생성되지 않았습니다."),
    DUTCH_PARTICIPANT_NOT_PAYABLE(HttpStatus.CONFLICT, "PAY-DUT-208", "DUTCH_PARTICIPANT_NOT_PAYABLE", "결제 가능한 참여자 상태가 아닙니다."),
    DUTCH_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "PAY-DUT-209", "DUTCH_AMOUNT_MISMATCH", "더치페이 결제 금액이 일치하지 않습니다."),
    DUTCH_PAYMENT_ALREADY_LINKED(HttpStatus.CONFLICT, "PAY-DUT-210", "DUTCH_PAYMENT_ALREADY_LINKED", "이미 연결된 결제 건입니다."),
    DUTCH_INVITE_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "PAY-DUT-211", "DUTCH_INVITE_TOKEN_INVALID", "유효하지 않은 더치페이 초대 링크입니다."),
    DUTCH_INVITE_TOKEN_EXPIRED(HttpStatus.GONE, "PAY-DUT-212", "DUTCH_INVITE_TOKEN_EXPIRED", "만료된 더치페이 초대 링크입니다."),
    DUTCH_DUPLICATED_PARTICIPANT(HttpStatus.CONFLICT, "PAY-DUT-301", "DUTCH_DUPLICATED_PARTICIPANT", "이미 참여 중인 사용자입니다."),
    DUTCH_CONCURRENT_UPDATE_CONFLICT(HttpStatus.CONFLICT, "PAY-DUT-302", "DUTCH_CONCURRENT_UPDATE_CONFLICT", "더치페이 정보가 동시에 변경되었습니다."),
    DUTCH_HOST_AUTH_REJECTED(HttpStatus.CONFLICT, "PAY-DUT-401", "DUTCH_HOST_AUTH_REJECTED", "대표자 가승인이 거절되었습니다."),
    DUTCH_HOST_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "PAY-DUT-402", "DUTCH_HOST_AUTH_UNAVAILABLE", "대표자 가승인 처리를 일시적으로 사용할 수 없습니다."),
    DUTCH_SSE_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-DUT-501", "DUTCH_SSE_PUBLISH_FAILED", "더치페이 실시간 상태 전파에 실패했습니다."),
    DUTCH_NOTIFICATION_PUBLISH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-DUT-502", "DUTCH_NOTIFICATION_PUBLISH_FAILED", "더치페이 알림 이벤트 발행에 실패했습니다."),

    // Internal / external dependency
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-CORE-900", "CORE_INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),
    INTERNAL_PG_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-PG-401", "PG_PAYMENT_UNAVAILABLE", "PG 서버 내부 오류가 발생했습니다."),
    INTERNAL_CARD_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "PAY-CARD-401", "CARD_SERVICE_UNAVAILABLE", "카드 서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;
}
