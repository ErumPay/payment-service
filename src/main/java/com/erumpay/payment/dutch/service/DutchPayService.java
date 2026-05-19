package com.erumpay.payment.dutch.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.dao.OrderRepository;
import com.erumpay.payment.core.domain.entity.OrderEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationRequest;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DutchPayService {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_RANDOM_DIGITS = 10;

    private final OrderRepository orderRepository;
    private final DutchPaySessionRepository dutchPaySessionRepository;

    @Transactional
    public DutchPayCreateResponse createSession(DutchPayCreateRequest request) {
        log.info("/api/v1/dutch-pay/sessions Service");
        validateCreateRequest(request);

        LocalDateTime now = LocalDateTime.now();
        DutchPaySessionEntity session = DutchPaySessionEntity.created(
                generateUniqueDutchOrderNo(now),
                request.getHost_user_id(),
                request.getMerchant_id(),
                request.getOrder_name(),
                request.getTotal_amount(),
                now);
        DutchPaySessionEntity savedSession = dutchPaySessionRepository.save(session);

        return DutchPayCreateResponse.fromEntity(savedSession, "CREATED");
    }

    @Transactional
    public DutchPayCreateResponse authorizeHostPayment(
            Long sessionId,
            DutchPayHostAuthorizationRequest request) {
        log.info("/api/v1/dutch-pay/sessions/{}/host-authorizations Service", sessionId);
        validateHostAuthorizationRequest(sessionId, request);

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        if (session.getStatus() != DutchPayStatus.CREATED || session.getHost_auth_payment() != null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        // [be] 영은 260519 1340 | PG 연동 전까지 대표자 가승인 성공을 AUTHORIZED 주문으로 임시 기록
        OrderEntity hostAuthPayment = OrderEntity.toDutchHostAuthEntity(
                generateUniqueOrderNo(now),
                session.getOrder_name(),
                session.getTotal_amount(),
                session.getHost_user_id(),
                session.getMerchant_id(),
                request.getIdempotency_key(),
                now);
        OrderEntity savedHostAuthPayment = orderRepository.save(hostAuthPayment);
        savedHostAuthPayment.connectDutchSession(session.getSession_id());

        session.authorizeHostPayment(savedHostAuthPayment);

        return DutchPayCreateResponse.fromEntity(session, "AUTHORIZED");
    }

    private void validateCreateRequest(DutchPayCreateRequest request) {
        if (request == null
                || request.getHost_user_id() == null
                || request.getMerchant_id() == null
                || request.getTotal_amount() == null
                || request.getTotal_amount() <= 0
                || request.getOrder_name() == null
                || request.getOrder_name().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateHostAuthorizationRequest(
            Long sessionId,
            DutchPayHostAuthorizationRequest request) {
        if (sessionId == null
                || request == null
                || request.getIdempotency_key() == null
                || request.getIdempotency_key().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private String generateUniqueOrderNo(LocalDateTime now) {
        String datePart = now.format(ORDER_DATE_FORMAT);
        String prefix = "ORD" + datePart + "EP";

        return Stream.generate(() -> ThreadLocalRandom.current().nextLong(10_000_000_000L))
                .map(randomNumber -> prefix + String.format("%0" + ORDER_RANDOM_DIGITS + "d", randomNumber))
                .filter(orderNo -> !orderRepository.existsByOrderNo(orderNo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to generate unique order_no"));
    }

    private String generateUniqueDutchOrderNo(LocalDateTime now) {
        String datePart = now.format(ORDER_DATE_FORMAT);
        String prefix = "DUTCH" + datePart + "EP";

        return Stream.generate(() -> ThreadLocalRandom.current().nextLong(10_000_000_000L))
                .map(randomNumber -> prefix + String.format("%0" + ORDER_RANDOM_DIGITS + "d", randomNumber))
                .filter(dutchOrderNo -> !dutchPaySessionRepository.existsByDutchOrderNo(dutchOrderNo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to generate unique dutch_order_no"));
    }
}
