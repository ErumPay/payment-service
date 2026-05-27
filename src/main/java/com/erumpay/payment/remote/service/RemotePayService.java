package com.erumpay.payment.remote.service;

import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.dto.PrepareResponse;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.core.service.CoreValidationService;
import com.erumpay.payment.qr.service.QrService;
import com.erumpay.payment.remote.dao.RemotePayRequestRepository;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayPreparePaymentResponse;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemotePayService {

    private final RemotePayRequestRepository remotePayRequestRepository;
    private final CoreRepository coreRepository;
    private final CoreValidationService coreValidationService;
    private final RemotePayFriendValidator remotePayFriendValidator;
    private final QrService qrService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.remote-pay.expires-after-minutes:30}")
    private long expiresAfterMinutes;

    public RemotePayCreateResponse createRequest(Long requesterUserId, RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Service");

        if (requesterUserId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        remotePayFriendValidator.validate(requesterUserId, request.getTarget_user_id());

        return transactionTemplate.execute(status -> savePendingRequest(requesterUserId, request));
    }

    private RemotePayCreateResponse savePendingRequest(Long requesterUserId, RemotePayCreateRequest request) {
        LocalDateTime now = LocalDateTime.now();
        RemotePayRequestEntity remoteRequest;
        try {
            remoteRequest = RemotePayRequestEntity.pending(
                    requesterUserId,
                    request.getTarget_user_id(),
                    request.getAmount(),
                    normalizeDescription(request.getDescription()),
                    now.plusMinutes(expiresAfterMinutes),
                    now);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayCreateResponse.fromEntity(remotePayRequestRepository.save(remoteRequest));
    }

    @Transactional
    public RemotePayPreparePaymentResponse preparePayment(
            Long targetUserId,
            String idempotencyKey,
            Long requestId) {
        log.info("/api/v1/remote-pay/requests/{}/prepare-payment Service", requestId);

        String normalizedIdempotencyKey = coreValidationService.normalizeIdempotencyKey(idempotencyKey);
        RemotePayRequestEntity request = getRequestForUpdate(requestId);

        if (targetUserId == null || !targetUserId.equals(request.getTarget_user_id())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        CoreEntity existingPayment = request.getPayment();
        if (existingPayment != null) {
            return RemotePayPreparePaymentResponse.fromEntity(request, existingPayment);
        }

        Optional<ResponseEntity<PrepareResponse>> idempotentResponse = coreValidationService.validateIdempotency(
                targetUserId,
                normalizedIdempotencyKey);
        if (idempotentResponse.isPresent()) {
            throw new CustomException(ErrorCode.DUPLICATED_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        CoreEntity payment = CoreEntity.toRemotePaymentEntity(
                qrService.generateUniqueOrderNo(now),
                buildOrderName(request),
                request.getAmount(),
                targetUserId,
                request.getRequest_id(),
                normalizedIdempotencyKey,
                now);

        try {
            payment = coreRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKey(e)) {
                throw new CustomException(ErrorCode.DUPLICATED_REQUEST, e);
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }

        try {
            request.assignPayment(payment, now);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayPreparePaymentResponse.fromEntity(request, payment);
    }

    @Transactional
    public RemotePayCreateResponse rejectRequest(Long targetUserId, Long requestId, String rejectReason) {
        log.info("/api/v1/remote-pay/requests/{}/reject Service", requestId);

        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.reject(targetUserId, normalizeDescription(rejectReason), LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayCreateResponse.fromEntity(request);
    }

    @Transactional
    public RemotePayCreateResponse cancelRequest(Long requesterUserId, Long requestId) {
        log.info("/api/v1/remote-pay/requests/{}/cancel Service", requestId);

        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.cancel(requesterUserId, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayCreateResponse.fromEntity(request);
    }

    @Transactional
    public void completeByPayment(CoreEntity payment) {
        if (payment == null || payment.getRemote_request_id() == null) {
            return;
        }

        RemotePayRequestEntity request = getRequestForUpdate(payment.getRemote_request_id());
        try {
            request.complete(payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
    }

    @Transactional
    public void validatePaymentCanBeRequested(CoreEntity payment) {
        if (payment == null || payment.getPayment_type() != CoreEntity.PaymentType.REMOTE) {
            return;
        }

        RemotePayRequestEntity request = getRequestForUpdate(payment.getRemote_request_id());
        try {
            request.requirePayable(payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    private RemotePayRequestEntity getRequestForUpdate(Long requestId) {
        if (requestId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return remotePayRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }

    private String buildOrderName(RemotePayRequestEntity request) {
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            return "원격결제";
        }

        return request.getDescription();
    }

    private boolean isDuplicateKey(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == 1062) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains("duplicate entry")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
