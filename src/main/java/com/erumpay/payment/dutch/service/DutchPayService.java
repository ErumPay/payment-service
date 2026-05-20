package com.erumpay.payment.dutch.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.dao.DutchPayParticipantRepository;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity.ParticipantStatus;
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

    private final DutchPaySessionRepository dutchPaySessionRepository;
    private final DutchPayParticipantRepository dutchPayParticipantRepository;

    @Transactional
    public DutchPayCreateResponse createSession(DutchPayCreateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions Service");
        validateCreateRequest(request);

        LocalDateTime now = LocalDateTime.now();
        // [be] 영은 260520 1530 | core prepare가 만든 대표자 payment_id를 받아 더치 세션만 생성
        DutchPaySessionEntity session = DutchPaySessionEntity.created(
                generateUniqueDutchOrderNo(now),
                request.getHost_payment_id(),
                request.getHost_user_id(),
                request.getMerchant_id(),
                request.getOrder_name(),
                request.getTotal_amount(),
                now);
        DutchPaySessionEntity savedSession = dutchPaySessionRepository.save(session);

        return DutchPayCreateResponse.fromEntity(savedSession, "CREATED");
    }

    @Transactional
    public DutchPayCreateResponse applyHostAuthorizationResult(
            Long sessionId,
            DutchPayHostAuthorizationResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/host-authorization-result Service", sessionId);
        validateHostAuthorizationResultRequest(sessionId, request);

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        if (!session.getHost_auth_payment_id().equals(request.getPayment_id())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        // [be] 영은 260520 1530 | 대표자 가승인 결과만 반영하고 payment_orders 상태 변경은 core가 담당
        session.applyHostAuthorizationResult("AUTHORIZED".equalsIgnoreCase(request.getStatus()));

        return DutchPayCreateResponse.fromEntity(session, request.getStatus());
    }

    @Transactional(readOnly = true)
    public DutchPayParticipantPaymentValidateResponse validateParticipantPayment(
            Long sessionId,
            DutchPayParticipantPaymentValidateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/participants/validate-payment Service", sessionId);
        validateParticipantPaymentRequest(sessionId, request);

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        // [be] 영은 260520 2039 | 참여자 결제 prepare 전 더치 세션이 결제 가능한 상태인지 먼저 검증
        if (session.getStatus() != DutchPayStatus.IN_PROGRESS) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findParticipantForPaymentValidation(
                        sessionId,
                        request.getParticipant_id(),
                        request.getUser_id())
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        // [be] 영은 260520 2039 | payment_orders 생성은 core가 담당하므로 더치는 참여자/금액/중복 여부만 검증
        if (participant.getStatus() != ParticipantStatus.PENDING
                || participant.getPayment() != null
                || participant.getAmount() == null
                || !participant.getAmount().equals(request.getAmount())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return DutchPayParticipantPaymentValidateResponse.valid(
                sessionId,
                participant.getParticipant_id(),
                participant.getUser_id(),
                participant.getAmount(),
                participant.getStatus().name());
    }

    private void validateCreateRequest(DutchPayCreateRequest request) {
        if (request == null
                || request.getHost_payment_id() == null
                || request.getHost_user_id() == null
                || request.getMerchant_id() == null
                || request.getTotal_amount() == null
                || request.getTotal_amount() <= 0
                || request.getOrder_name() == null
                || request.getOrder_name().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateHostAuthorizationResultRequest(
            Long sessionId,
            DutchPayHostAuthorizationResultRequest request) {
        if (sessionId == null
                || request == null
                || request.getPayment_id() == null
                || request.getStatus() == null
                || request.getStatus().isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void validateParticipantPaymentRequest(
            Long sessionId,
            DutchPayParticipantPaymentValidateRequest request) {
        if (sessionId == null
                || request == null
                || request.getParticipant_id() == null
                || request.getUser_id() == null
                || request.getAmount() == null
                || request.getAmount() <= 0) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
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
