package com.erumpay.payment.core.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodValidationException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class
    })
    protected ResponseEntity<ErrorResponse> handleValidationException(Exception e) {
        log.error("handleValidationException [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.BAD_REQUEST);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    protected ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(MissingRequestHeaderException e) {
        log.warn("handleMissingRequestHeaderException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.REQUEST_HEADER_REQUIRED);
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    protected ResponseEntity<ErrorResponse> handleRequestParameterException(Exception e) {
        log.warn("handleRequestParameterException [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.REQUEST_PARAMETER_INVALID);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    protected ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("handleMethodNotSupportedException : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.REQUEST_METHOD_NOT_SUPPORTED);
    }

    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })
    protected ResponseEntity<ErrorResponse> handleNotFoundException(Exception e) {
        log.warn("handleNotFoundException [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.REQUEST_PATH_NOT_FOUND);
    }

    // custom 예외 처리
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("handleCustomException throw CustomException : {}", e.getErrorCode());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // 기타 예외
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handlerException(Exception e) {
        log.error("handleException throw Exception : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
