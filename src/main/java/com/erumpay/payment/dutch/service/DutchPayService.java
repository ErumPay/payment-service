package com.erumpay.payment.dutch.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.dao.DutchPayParticipantRepository;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
import com.erumpay.payment.dutch.domain.dto.DutchPayAmountRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostFinalPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteLinkResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayMyPaymentResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantsConfirmRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPaySplitMethodRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayTimeoutBatchResponse;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity.ParticipantStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.SplitMethod;
import com.erumpay.payment.notification.service.PaymentNotificationEventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DutchPayService {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_RANDOM_DIGITS = 10;
    private static final long INVITE_TOKEN_TTL_MILLIS = 3L * 60L * 60L * 1000L;
    private static final Duration WARNING_1_AFTER = Duration.ofMinutes(15);
    private static final Duration WARNING_2_AFTER = Duration.ofMinutes(25);
    private static final Duration TIMEOUT_AFTER = Duration.ofMinutes(30);
    private static final String INVITE_TOKEN_HMAC_ALGORITHM = "HmacSHA256";
    private static final String DEFAULT_INVITE_TOKEN_SECRET = "erumpay-local-dutch-invite-token-secret";
    private static final String PARTICIPANT_PAYMENT_STATUS_PAID = "PAID";
    private static final String PARTICIPANT_PAYMENT_STATUS_FAILED = "FAILED";

    private final DutchPaySessionRepository dutchPaySessionRepository;
    private final DutchPayParticipantRepository dutchPayParticipantRepository;
    private final CoreRepository coreRepository;
    private final DutchPaySseService dutchPaySseService;
    private final PaymentNotificationEventPublisher notificationEventPublisher;

    // [be] 영은 260523 1120 | core에서 생성한 대표자 payment_id를 받아 더치페이 세션과 대표자 참여자 row를 만든다
    @Transactional
    public DutchPayCreateResponse createSession(DutchPayCreateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions Service");
        validateCreateRequest(request);

        LocalDateTime now = LocalDateTime.now();
        DutchPaySessionEntity session = DutchPaySessionEntity.created(
                generateUniqueDutchOrderNo(now),
                request.getHost_payment_id(),
                request.getHost_user_id(),
                request.getMerchant_id(),
                request.getOrder_name(),
                request.getTotal_amount(),
                now);
        DutchPaySessionEntity savedSession = dutchPaySessionRepository.save(session);
        dutchPayParticipantRepository.save(DutchPayParticipantEntity.host(savedSession, request.getHost_user_id(), now));

        return DutchPayCreateResponse.fromEntity(savedSession, "CREATED");
    }

    // [be] 영은 260523 1120 | 대표자 가승인 성공/실패 결과를 세션 상태에 반영하고 화면 구독자에게 공유한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DutchPayCreateResponse applyHostAuthorizationResult(
            Long sessionId,
            DutchPayHostAuthorizationResultRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/host-authorization-result Service", sessionId);
        validateHostAuthorizationResultRequest(sessionId, request);

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_SESSION_NOT_FOUND));
        if (!session.getHost_auth_payment_id().equals(request.getPayment_id())) {
            throw new CustomException(ErrorCode.DUTCH_HOST_AUTH_NOT_CREATED);
        }

        session.applyHostAuthorizationResult("AUTHORIZED".equalsIgnoreCase(request.getStatus()));
        publishSessionUpdated(sessionId, "HOST_AUTHORIZATION_UPDATED");

        return DutchPayCreateResponse.fromEntity(session, request.getStatus());
    }

    // [be] 영은 260523 1120 | core가 참여자 결제 생성 전 dutch 세션 상태와 참여자 부담 금액을 검증한다
    @Transactional(readOnly = true)
    public DutchPayParticipantPaymentValidateResponse validateParticipantPayment(
            Long sessionId,
            DutchPayParticipantPaymentValidateRequest request) {
        log.info("/internal/v1/dutch-pay/sessions/{}/participants/validate-payment Service", sessionId);
        validateParticipantPaymentRequest(sessionId, request);

        DutchPaySessionEntity session = dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_SESSION_NOT_FOUND));
        if (session.getStatus() != DutchPayStatus.IN_PROGRESS) {
            throw sessionStateException(session);
        }

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findParticipantForPaymentValidation(
                        sessionId,
                        request.getUser_id())
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        if (participant.getStatus() != ParticipantStatus.PENDING
                || participant.getPayment() != null
                || participant.getAmount() == null) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }
        if (!participant.getAmount().equals(request.getAmount())) {
            throw new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH);
        }

        return DutchPayParticipantPaymentValidateResponse.valid(
                sessionId,
                participant.getUser_id(),
                participant.getAmount(),
                participant.getStatus().name());
    }

    // [be] 영은 260603 | Core가 대표자 최종 결제 주문을 만들기 전에 세션/대표자/최종 부담금을 검증한다.
    @Transactional(readOnly = true)
    public void validateHostFinalPayment(
            Long sessionId,
            Long userId,
            Long amount) {
        if (sessionId == null || userId == null || amount == null || amount <= 0) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        if (!userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_HOST_ONLY_ACTION);
        }
        if (session.getStatus() != DutchPayStatus.IN_PROGRESS
                && session.getStatus() != DutchPayStatus.TIMEOUT_HANDLED) {
            throw sessionStateException(session);
        }

        DutchPayParticipantEntity host = dutchPayParticipantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        if (host.getStatus() != ParticipantStatus.PENDING
                || host.getPayment() != null
                || host.getAmount() == null) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }
        if (!host.getAmount().equals(amount)) {
            throw new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH);
        }
    }

    // [be] 영은 260526 1620 | core가 참여자 결제 주문을 만들면 더치 참여자 row에 payment_id를 연결한다
    @Transactional
    public void registerParticipantPayment(
            Long sessionId,
            Long userId,
            CoreEntity payment) {
        if (sessionId == null
                || userId == null
                || payment == null
                || payment.getPaymentId() == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findParticipantForPaymentUpdate(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        if (userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }

        try {
            participant.startPayment(payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw toParticipantPaymentException(e);
        }

        publishSessionUpdated(sessionId, "PARTICIPANT_PAYMENT_CREATED");
    }

    // [be] 영은 260601 | 참여자 결제 완료를 반영하고, 전원 결제 시 대표자 최종 결제 대기 상태를 알린다.
    @Transactional
    public DutchPaySessionDetailResponse applyParticipantPaymentResult(
            Long sessionId,
            DutchPayParticipantPaymentResultRequest request) {
        validateParticipantPaymentResultRequest(sessionId, request);

        DutchPaySessionEntity session = getSessionForPaymentResultUpdate(sessionId);
        ensureInProgress(session);

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findParticipantForPaymentResultUpdate(
                        sessionId,
                        request.getUser_id(),
                        request.getPayment_id())
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        if (request.getUser_id().equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        boolean paidResult = isParticipantPaymentPaid(request.getStatus());

        try {
            if (paidResult) {
                participant.completePayment(request.getPayment_id(), now);
            } else if (isParticipantPaymentFailed(request.getStatus())) {
                participant.failPayment(request.getPayment_id(), now);
            } else {
                throw new IllegalArgumentException("Unsupported participant payment status");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw toParticipantPaymentException(e);
        }

        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        if (!paidResult) {
            recalculateHostFinalAmount(session, participants, now);
        }
        boolean allMembersPaid = allPayableMembersPaid(session, participants);

        return publishAndReturn(
                sessionId,
                allMembersPaid
                        ? "HOST_FINAL_PAYMENT_REQUIRED"
                        : paidResult
                                ? "PARTICIPANT_PAYMENT_PAID"
                                : "PARTICIPANT_PAYMENT_FAILED");
    }

    // [be] 영은 260601 | Core가 대표자 최종 결제 완료를 알려주면 대표자 결제 상태와 세션 완료를 확정한다.
    @Transactional
    public DutchPaySessionDetailResponse applyHostFinalPaymentResult(
            Long sessionId,
            DutchPayHostFinalPaymentResultRequest request) {
        validateHostFinalPaymentResultRequest(sessionId, request);

        DutchPaySessionEntity session = getSessionForPaymentResultUpdate(sessionId);
        if (!request.getUser_id().equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_HOST_ONLY_ACTION);
        }
        if (session.getStatus() != DutchPayStatus.IN_PROGRESS
                && session.getStatus() != DutchPayStatus.TIMEOUT_HANDLED) {
            throw sessionStateException(session);
        }

        CoreEntity payment = coreRepository.findById(request.getPayment_id())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));
        if (!request.getUser_id().equals(payment.getUserId())) {
            throw new CustomException(ErrorCode.DUTCH_ACCESS_DENIED);
        }

        DutchPayParticipantEntity host = getParticipants(sessionId).stream()
                .filter(participant -> participant.getUser_id().equals(session.getHost_user_id()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));

        try {
            LocalDateTime now = LocalDateTime.now();
            host.completeHostFinalPayment(payment, now);
            session.complete(now);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw toParticipantPaymentException(e);
        }

        getParticipants(sessionId).forEach(participant -> notificationEventPublisher.publishDutchCompleted(
                sessionId,
                participant.getUser_id(),
                request.getPayment_id()));
        return publishAndReturn(sessionId, "SESSION_COMPLETED");
    }

    @Transactional
    public DutchPayTimeoutBatchResponse handleTimeoutBatch() {
        LocalDateTime now = LocalDateTime.now();

        List<DutchPayTimeoutBatchResponse.TimeoutHandledSession> timeoutResults = new ArrayList<>();
        List<Long> failedSessionIds = new ArrayList<>();
        List<DutchPaySessionEntity> timeoutTargets = dutchPaySessionRepository.findTimeoutTargetsForUpdate(
                DutchPayStatus.IN_PROGRESS,
                now.minus(TIMEOUT_AFTER));
        for (DutchPaySessionEntity session : timeoutTargets) {
            try {
                timeoutResults.add(handleTimedOutSession(session, now));
            } catch (RuntimeException e) {
                failedSessionIds.add(session.getSession_id());
                log.warn("DutchPay timeout handling failed. session_id={}", session.getSession_id(), e);
            }
        }

        List<DutchPaySessionEntity> warning2Targets = dutchPaySessionRepository.findWarning2TargetsForUpdate(
                DutchPayStatus.IN_PROGRESS,
                now.minus(WARNING_2_AFTER));
        int warning2Count = 0;
        for (DutchPaySessionEntity session : warning2Targets) {
            try {
                session.markWarning2Sent(now);
                publishSessionUpdated(session.getSession_id(), "TIMEOUT_WARNING_2");
                notificationEventPublisher.publishDutchTimeoutWarning2(
                        session.getSession_id(),
                        session.getHost_user_id(),
                        session.getHost_auth_payment_id());
                warning2Count++;
            } catch (RuntimeException e) {
                failedSessionIds.add(session.getSession_id());
                log.warn("DutchPay timeout warning2 failed. session_id={}", session.getSession_id(), e);
            }
        }

        List<DutchPaySessionEntity> warning1Targets = dutchPaySessionRepository.findWarning1TargetsForUpdate(
                DutchPayStatus.IN_PROGRESS,
                now.minus(WARNING_1_AFTER));
        int warning1Count = 0;
        for (DutchPaySessionEntity session : warning1Targets) {
            try {
                session.markWarning1Sent(now);
                publishSessionUpdated(session.getSession_id(), "TIMEOUT_WARNING_1");
                notificationEventPublisher.publishDutchTimeoutWarning1(
                        session.getSession_id(),
                        session.getHost_user_id(),
                        session.getHost_auth_payment_id());
                warning1Count++;
            } catch (RuntimeException e) {
                failedSessionIds.add(session.getSession_id());
                log.warn("DutchPay timeout warning1 failed. session_id={}", session.getSession_id(), e);
            }
        }

        return DutchPayTimeoutBatchResponse.builder()
                .warning_1_count(warning1Count)
                .warning_2_count(warning2Count)
                .timeout_handled_count(timeoutResults.size())
                .failed_count(failedSessionIds.size())
                .failed_session_ids(failedSessionIds)
                .timeout_sessions(timeoutResults)
                .build();
    }

    // [be] 영은 260523 1120 | 대표자/참여자가 알림 클릭 또는 화면 복원 시 최신 세션 상태를 조회한다
    @Transactional
    public DutchPaySessionDetailResponse getSession(Long userId, Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        handleTimeoutOnReadIfExpired(session, LocalDateTime.now());

        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        ensureSessionMember(session, participants, userId);

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    // [be] 영은 260523 1120 | 참여자가 결제 화면에 진입할 때 본인 participant_id와 부담 금액을 조회한다
    @Transactional
    public DutchPayMyPaymentResponse getMyPayment(Long userId, Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        handleTimeoutOnReadIfExpired(session, LocalDateTime.now());

        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        ensureSessionMember(session, participants, userId);

        DutchPayParticipantEntity participant = participants.stream()
                .filter(item -> item.getUser_id().equals(userId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));

        return DutchPayMyPaymentResponse.fromEntity(session, participant);
    }

    // [be] 영은 260523 1120 | 홈 화면 진행 중 더치페이 목록 조회에 사용한다
    @Transactional
    public List<DutchPaySessionDetailResponse> getActiveSessions(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        return dutchPaySessionRepository.findActiveSessionsByUserId(
                userId,
                List.of(DutchPayStatus.CREATED, DutchPayStatus.IN_PROGRESS)).stream()
                .map(session -> {
                    handleTimeoutOnReadIfExpired(session, now);
                    return session;
                })
                .filter(session -> session.getStatus() != DutchPayStatus.TIMEOUT_HANDLED)
                .map(session -> DutchPaySessionDetailResponse.fromEntity(
                        session,
                        getParticipants(session.getSession_id())))
                .toList();
    }

    // [be] 영은 260523 1120 | 대표자가 앱 친구를 초대하면 참여자 row를 INVITED 상태로 추가한다
    @Transactional
    public DutchPaySessionDetailResponse inviteAppFriends(
            Long hostUserId,
            Long sessionId,
            DutchPayInviteRequest request) {
        validateInviteRequest(hostUserId, sessionId, request);

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureHostInProgress(session, hostUserId);

        LocalDateTime now = LocalDateTime.now();
        Set<Long> uniqueUserIds = new HashSet<>(request.getUser_ids());
        for (Long inviteeUserId : uniqueUserIds) {
            if (inviteeUserId == null
                    || inviteeUserId.equals(session.getHost_user_id())
                    || dutchPayParticipantRepository.existsBySessionIdAndUserId(sessionId, inviteeUserId)) {
                throw new CustomException(ErrorCode.DUTCH_DUPLICATED_PARTICIPANT);
            }

            dutchPayParticipantRepository.save(
                    DutchPayParticipantEntity.invited(session, inviteeUserId, null, now));
            notificationEventPublisher.publishDutchInvited(sessionId, inviteeUserId, session.getOrder_name());
        }

        return publishAndReturn(sessionId, "PARTICIPANTS_INVITED");
    }

    // [be] 영은 260523 1120 | 앱 밖 공유용 초대 링크에 session_id를 서명 토큰으로 감싸서 발급한다
    @Transactional(readOnly = true)
    public DutchPayInviteLinkResponse createInviteLink(Long hostUserId, Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureHostInProgress(session, hostUserId);

        String inviteToken = createSignedInviteToken(sessionId);

        return DutchPayInviteLinkResponse.builder()
                .session_id(sessionId)
                .invite_token(inviteToken)
                .invite_url("/api/v1/dutch-pay/invite-links/" + inviteToken + "/accept")
                .build();
    }

    // [be] 영은 260523 1120 | 링크 수락 시 토큰을 검증하고 참여자를 INVITED 상태로 추가한다
    @Transactional
    public DutchPaySessionDetailResponse acceptInviteLink(Long userId, String inviteToken) {
        if (userId == null || inviteToken == null || inviteToken.isBlank()) {
            throw new CustomException(ErrorCode.DUTCH_INVITE_TOKEN_INVALID);
        }

        Long sessionId = parseSessionIdFromInviteToken(inviteToken);
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);

        if (userId.equals(session.getHost_user_id())
                || dutchPayParticipantRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new CustomException(ErrorCode.DUTCH_DUPLICATED_PARTICIPANT);
        }

        dutchPayParticipantRepository.save(
                DutchPayParticipantEntity.invited(session, userId, null, LocalDateTime.now()));

        return publishAndReturn(sessionId, "INVITE_LINK_ACCEPTED");
    }

    // [be] 영은 260523 1120 | 참여자가 초대를 거절하면 REJECTED로 바꾸고 CUSTOM 금액은 다시 계산한다
    @Transactional
    public DutchPaySessionDetailResponse rejectInvite(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        if (userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }

        participant.reject(LocalDateTime.now());
        if (session.getSplit_method() == SplitMethod.CUSTOM) {
            recalculateHostCustomAmount(session, getParticipants(sessionId), LocalDateTime.now());
        }

        return publishAndReturn(sessionId, "INVITE_REJECTED");
    }

    // [be] 영은 260523 1120 | 대표자가 인원을 확정하면 INVITED 참여자를 PENDING으로 전환한다
    @Transactional
    public DutchPaySessionDetailResponse confirmParticipants(
            Long hostUserId,
            Long sessionId,
            DutchPayParticipantsConfirmRequest request) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureHostInProgress(session, hostUserId);

        LocalDateTime now = LocalDateTime.now();
        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        if (participants.isEmpty()) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND);
        }

        participants.forEach(participant -> participant.confirm(now));
        participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .forEach(participant -> notificationEventPublisher.publishDutchConfirmed(
                        sessionId,
                        participant.getUser_id(),
                        session.getOrder_name()));

        if (request != null && request.getSplit_method() != null && !request.getSplit_method().isBlank()) {
            SplitMethod splitMethod = parseSplitMethod(request.getSplit_method());
            session.changeSplitMethod(splitMethod, now);
            applySplitMethod(session, participants, now);
        }

        return publishAndReturn(sessionId, "PARTICIPANTS_CONFIRMED");
    }

    // [be] 영은 260523 1120 | EQUAL은 즉시 균등 배분하고 CUSTOM은 대표자에게 전체 금액을 둔다
    @Transactional
    public DutchPaySessionDetailResponse updateSplitMethod(
            Long hostUserId,
        Long sessionId,
        DutchPaySplitMethodRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureHostInProgress(session, hostUserId);

        LocalDateTime now = LocalDateTime.now();
        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        SplitMethod splitMethod = parseSplitMethod(request.getSplit_method());
        session.changeSplitMethod(splitMethod, now);
        applySplitMethod(session, participants, now);

        return publishAndReturn(sessionId, "SPLIT_METHOD_UPDATED");
    }

    // [be] 영은 260523 1120 | CUSTOM에서 참여자 금액을 저장하고 대표자 잔여 부담금을 재계산한다
    @Transactional
    public DutchPaySessionDetailResponse updateMyAmount(
            Long userId,
            Long sessionId,
            DutchPayAmountRequest request) {
        if (userId == null || sessionId == null || request == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);
        if (session.getSplit_method() != SplitMethod.CUSTOM || userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        participant.updateAmount(request.getAmount(), LocalDateTime.now());

        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        recalculateHostCustomAmount(session, participants, LocalDateTime.now());

        return publishAndReturn(sessionId, "AMOUNT_UPDATED");
    }

    // [be] 영은 260523 1120 | 내부 세션 생성 요청의 필수값과 금액 유효성을 검증한다
    private void validateCreateRequest(DutchPayCreateRequest request) {
        if (request == null
                || request.getHost_payment_id() == null
                || request.getHost_user_id() == null
                || request.getMerchant_id() == null
                || request.getTotal_amount() == null
                || request.getTotal_amount() <= 0
                || request.getOrder_name() == null
                || request.getOrder_name().isBlank()) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    private DutchPayTimeoutBatchResponse.TimeoutHandledSession handleTimedOutSession(
            DutchPaySessionEntity session,
            LocalDateTime now) {
        List<DutchPayParticipantEntity> participants = getParticipants(session.getSession_id());

        long paidMemberAmount = participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .filter(participant -> participant.getStatus() == ParticipantStatus.PAID)
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);
        long hostFinalAmount = session.getTotal_amount() - paidMemberAmount;
        if (hostFinalAmount < 0) {
            throw new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH);
        }

        DutchPayParticipantEntity host = participants.stream()
                .filter(participant -> participant.getUser_id().equals(session.getHost_user_id()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));
        host.assignAmount(hostFinalAmount, now);

        participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .forEach(participant -> participant.timeout(now));

        session.timeoutHandled(now);
        publishSessionUpdated(session.getSession_id(), "TIMEOUT_HANDLED");

        return DutchPayTimeoutBatchResponse.TimeoutHandledSession.builder()
                .session_id(session.getSession_id())
                .host_user_id(session.getHost_user_id())
                .host_auth_payment_id_to_void(session.getHost_auth_payment_id())
                .host_final_amount(hostFinalAmount)
                .host_final_payment_required(hostFinalAmount > 0)
                .build();
    }

    // [be] 조보름 260607 1010 | 사용자가 화면 조회 중이면 배치 대기 없이 만료된 더치페이를 즉시 타임아웃 처리한다
    private void handleTimeoutOnReadIfExpired(DutchPaySessionEntity session, LocalDateTime now) {
        if (session.getStatus() != DutchPayStatus.IN_PROGRESS || session.getCreated_at() == null) {
            return;
        }

        if (!session.getCreated_at().plus(TIMEOUT_AFTER).isAfter(now)) {
            handleTimedOutSession(session, now);
        }
    }

    // [be] 영은 260523 1120 | 대표자 가승인 결과 콜백의 세션/결제 식별자를 검증한다
    private void validateHostAuthorizationResultRequest(
            Long sessionId,
            DutchPayHostAuthorizationResultRequest request) {
        if (sessionId == null
                || request == null
                || request.getPayment_id() == null
                || request.getStatus() == null
                || request.getStatus().isBlank()) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    // [be] 영은 260523 1120 | 참여자 결제 검증 요청의 참여자/회원/금액 식별자를 검증한다
    private void validateParticipantPaymentRequest(
            Long sessionId,
            DutchPayParticipantPaymentValidateRequest request) {
        if (sessionId == null
                || request == null
                || request.getUser_id() == null
                || request.getAmount() == null
                || request.getAmount() <= 0) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    // [be] 영은 260526 1620 | 참여자 결제 완료 콜백의 세션/참여자/결제 식별자와 성공 상태를 검증한다
    private void validateParticipantPaymentResultRequest(
            Long sessionId,
            DutchPayParticipantPaymentResultRequest request) {
        if (sessionId == null
                || request == null
                || request.getUser_id() == null
                || request.getPayment_id() == null
                || request.getStatus() == null
                || request.getStatus().isBlank()
                || (!isParticipantPaymentPaid(request.getStatus())
                && !isParticipantPaymentFailed(request.getStatus()))) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    // [be] 영은 260523 1120 | 앱 친구 초대 요청의 대표자/세션/초대 대상 목록을 검증한다
    // [be] 영은 260601 | 대표자 최종 결제 완료 콜백의 세션/대표자/결제 식별자와 성공 상태를 검증한다.
    private void validateHostFinalPaymentResultRequest(
            Long sessionId,
            DutchPayHostFinalPaymentResultRequest request) {
        if (sessionId == null
                || request == null
                || request.getUser_id() == null
                || request.getPayment_id() == null
                || request.getStatus() == null
                || request.getStatus().isBlank()
                || (!"PAID".equalsIgnoreCase(request.getStatus())
                && !"APPROVED".equalsIgnoreCase(request.getStatus()))) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    private void validateInviteRequest(
            Long hostUserId,
            Long sessionId,
            DutchPayInviteRequest request) {
        if (hostUserId == null
                || sessionId == null
                || request == null
                || request.getUser_ids() == null
                || request.getUser_ids().isEmpty()) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
    }

    // [be] 영은 260523 1120 | 세션 조회 공통 처리
    private DutchPaySessionEntity getSessionOrThrow(Long sessionId) {
        if (sessionId == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        return dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_SESSION_NOT_FOUND));
    }

    // Serializes participant payment callbacks per session so the final callback cannot miss session completion.
    private DutchPaySessionEntity getSessionForPaymentResultUpdate(Long sessionId) {
        if (sessionId == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }

        return dutchPaySessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_SESSION_NOT_FOUND));
    }

    // [be] 영은 260523 1120 | 화면 응답과 배분 계산에 필요한 참여자 목록을 participant_id 순서로 조회한다
    private List<DutchPayParticipantEntity> getParticipants(Long sessionId) {
        return dutchPayParticipantRepository.findBySessionIdOrderByParticipantId(sessionId);
    }

    // [be] 영은 260523 1120 | 현재 세션과 참여자 목록을 화면 응답 DTO로 변환한다
    private DutchPaySessionDetailResponse toDetailResponse(Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        return DutchPaySessionDetailResponse.fromEntity(session, getParticipants(sessionId));
    }

    // [be] 영은 260523 1120 | 상태 변경 API 응답과 동일한 데이터를 SSE 이벤트로도 발행한다
    private DutchPaySessionDetailResponse publishAndReturn(Long sessionId, String eventType) {
        DutchPaySessionDetailResponse response = toDetailResponse(sessionId);
        publishAfterCommit(sessionId, eventType, response);
        return response;
    }

    // [be] 영은 260523 1120 | 응답 DTO가 필요 없는 내부 상태 변경에서도 SSE 이벤트를 예약한다
    private void publishSessionUpdated(Long sessionId, String eventType) {
        publishAfterCommit(sessionId, eventType, toDetailResponse(sessionId));
    }

    // [be] 영은 260523 1120 | DB 커밋 성공 이후에만 SSE를 발행해 화면과 저장 상태 불일치를 막는다
    private void publishAfterCommit(
            Long sessionId,
            String eventType,
            DutchPaySessionDetailResponse response) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dutchPaySseService.publishSessionUpdated(sessionId, eventType, response);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dutchPaySseService.publishSessionUpdated(sessionId, eventType, response);
            }
        });
    }

    // [be] 영은 260523 1120 | 대표자만 수행 가능한 IN_PROGRESS 세션 작업인지 확인한다
    private void ensureHostInProgress(DutchPaySessionEntity session, Long hostUserId) {
        try {
            session.requireHost(hostUserId);
            session.requireInProgress();
        } catch (IllegalStateException e) {
            if (hostUserId == null || !session.getHost_user_id().equals(hostUserId)) {
                throw new CustomException(ErrorCode.DUTCH_HOST_ONLY_ACTION, e);
            }
            throw sessionStateException(session, e);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST, e);
        }
    }

    // [be] 영은 260523 1120 | 초대/금액 입력 등 참여자 작업이 가능한 진행 중 세션인지 확인한다
    private void ensureInProgress(DutchPaySessionEntity session) {
        try {
            session.requireInProgress();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw sessionStateException(session, e);
        }
    }

    // [be] 영은 260523 1120 | 세션 상세/SSE/내 결제 조회자가 대표자 또는 참여자인지 확인한다
    private void ensureSessionMember(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST);
        }
        if (session.getHost_user_id().equals(userId)) {
            return;
        }

        boolean participant = participants.stream()
                .anyMatch(item -> item.getUser_id().equals(userId));
        if (!participant) {
            throw new CustomException(ErrorCode.DUTCH_ACCESS_DENIED);
        }
    }

    // [be] 영은 260523 1120 | 문자열 배분 방식을 enum으로 변환한다
    private SplitMethod parseSplitMethod(String splitMethod) {
        try {
            return SplitMethod.valueOf(splitMethod.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.DUTCH_INVALID_REQUEST, e);
        }
    }

    // [be] 영은 260523 1120 | 선택한 배분 방식에 따라 참여자 금액을 초기화하거나 계산한다
    private CustomException sessionStateException(DutchPaySessionEntity session) {
        return sessionStateException(session, null);
    }

    private CustomException sessionStateException(DutchPaySessionEntity session, Throwable cause) {
        if (session == null || session.getStatus() == null) {
            return new CustomException(ErrorCode.DUTCH_INVALID_REQUEST, cause);
        }
        if (session.getStatus() == DutchPayStatus.COMPLETED) {
            return new CustomException(ErrorCode.DUTCH_SESSION_ALREADY_COMPLETED, cause);
        }
        if (session.getStatus() == DutchPayStatus.FAILED) {
            return new CustomException(ErrorCode.DUTCH_SESSION_FAILED, cause);
        }
        if (session.getStatus() == DutchPayStatus.TIMEOUT_HANDLED) {
            return new CustomException(ErrorCode.DUTCH_SESSION_TIMEOUT_HANDLED, cause);
        }
        return new CustomException(ErrorCode.DUTCH_SESSION_NOT_IN_PROGRESS, cause);
    }

    private CustomException toParticipantPaymentException(RuntimeException e) {
        String message = e.getMessage();
        if (DutchPayParticipantEntity.ERROR_PAYMENT_ALREADY_ASSIGNED.equals(message)) {
            return new CustomException(ErrorCode.DUTCH_PAYMENT_ALREADY_LINKED, e);
        }
        if (DutchPayParticipantEntity.ERROR_AMOUNT_MISMATCH.equals(message)) {
            return new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH, e);
        }
        return new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE, e);
    }

    private void applySplitMethod(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        if (session.getSplit_method() == SplitMethod.EQUAL) {
            distributeEqualAmount(session, participants, now);
            return;
        }

        participants.forEach(participant -> {
            if (participant.getUser_id().equals(session.getHost_user_id())) {
                participant.assignAmount(session.getTotal_amount(), now);
                return;
            }

            participant.clearAmount(now);
        });
    }

    // [be] 영은 260523 1120 | PENDING 참여자 기준으로 균등 배분하고 나머지는 대표자에게 배정한다
    private void distributeEqualAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        List<DutchPayParticipantEntity> payableParticipants = participants.stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.PENDING)
                .toList();
        if (payableParticipants.isEmpty()) {
            throw new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_PAYABLE);
        }

        long baseAmount = session.getTotal_amount() / payableParticipants.size();
        long remainder = session.getTotal_amount() % payableParticipants.size();
        for (DutchPayParticipantEntity participant : payableParticipants) {
            long amount = baseAmount;
            if (participant.getUser_id().equals(session.getHost_user_id())) {
                amount += remainder;
            }
            participant.assignAmount(amount, now);
        }
    }

    // [be] 영은 260523 1120 | CUSTOM에서 참여자 입력 금액을 제외한 잔액을 대표자 부담금으로 반영한다
    private void recalculateHostCustomAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        DutchPayParticipantEntity host = participants.stream()
                .filter(participant -> participant.getUser_id().equals(session.getHost_user_id()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));

        long memberAmountSum = participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .filter(participant -> participant.getStatus() == ParticipantStatus.PENDING)
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);
        if (memberAmountSum > session.getTotal_amount()) {
            throw new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH);
        }

        host.assignAmount(session.getTotal_amount() - memberAmountSum, now);
    }

    // [be] 조보름 260607 1045 | 참여자 결제 실패/타임아웃 후 성공 결제액을 제외한 잔액을 대표자 부담금으로 반영한다
    private long recalculateHostFinalAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        DutchPayParticipantEntity host = participants.stream()
                .filter(participant -> participant.getUser_id().equals(session.getHost_user_id()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.DUTCH_PARTICIPANT_NOT_FOUND));

        long paidMemberAmount = participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .filter(participant -> participant.getStatus() == ParticipantStatus.PAID)
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);
        long hostFinalAmount = session.getTotal_amount() - paidMemberAmount;
        if (hostFinalAmount < 0) {
            throw new CustomException(ErrorCode.DUTCH_AMOUNT_MISMATCH);
        }

        host.assignAmount(hostFinalAmount, now);
        return hostFinalAmount;
    }

    // [be] 영은 260526 1620 | 대표자를 제외한 실제 부담 참여자가 모두 PAID인지 확인한다
    private boolean allPayableMembersPaid(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        return participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .filter(participant -> participant.getStatus() != ParticipantStatus.REJECTED)
                .allMatch(participant -> participant.getStatus() == ParticipantStatus.PAID);
    }

    private boolean isParticipantPaymentPaid(String status) {
        return PARTICIPANT_PAYMENT_STATUS_PAID.equalsIgnoreCase(status)
                || "APPROVED".equalsIgnoreCase(status);
    }

    private boolean isParticipantPaymentFailed(String status) {
        return PARTICIPANT_PAYMENT_STATUS_FAILED.equalsIgnoreCase(status);
    }

    // [be] 영은 260523 1120 | 초대 링크 토큰의 형식, 서명, 만료 시간을 검증하고 session_id를 추출한다
    private Long parseSessionIdFromInviteToken(String inviteToken) {
        try {
            String[] tokenParts = inviteToken.split("\\.");
            if (tokenParts.length != 2) {
                throw new IllegalArgumentException("Invalid invite token format");
            }

            String payload = new String(Base64.getUrlDecoder().decode(tokenParts[0]), StandardCharsets.UTF_8);
            String expectedSignature = signInviteTokenPayload(payload);
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    tokenParts[1].getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid invite token signature");
            }

            String[] payloadParts = payload.split(":");
            if (payloadParts.length != 3) {
                throw new IllegalArgumentException("Invalid invite token payload");
            }

            long expiresAtMillis = Long.parseLong(payloadParts[1]);
            if (Instant.now().toEpochMilli() > expiresAtMillis) {
                throw new CustomException(ErrorCode.DUTCH_INVITE_TOKEN_EXPIRED);
            }

            return Long.valueOf(payloadParts[0]);
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.DUTCH_INVITE_TOKEN_INVALID, e);
        }
    }

    // [be] 영은 260523 1120 | session_id와 만료 시각을 담은 서명 토큰을 생성한다
    private String createSignedInviteToken(Long sessionId) {
        String payload = sessionId
                + ":"
                + (Instant.now().toEpochMilli() + INVITE_TOKEN_TTL_MILLIS)
                + ":"
                + ThreadLocalRandom.current().nextLong();
        String encodedPayload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        return encodedPayload + "." + signInviteTokenPayload(payload);
    }

    // [be] 영은 260523 1120 | 초대 링크 위변조 방지를 위해 HMAC-SHA256 서명을 만든다
    private String signInviteTokenPayload(String payload) {
        try {
            Mac mac = Mac.getInstance(INVITE_TOKEN_HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveInviteTokenSecret().getBytes(StandardCharsets.UTF_8),
                    INVITE_TOKEN_HMAC_ALGORITHM));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    // [be] 영은 260523 1120 | 로컬 개발은 기본 secret을 쓰고 운영 환경은 환경변수로 교체한다
    private String resolveInviteTokenSecret() {
        String secret = System.getenv("DUTCH_INVITE_TOKEN_SECRET");
        if (secret == null || secret.isBlank()) {
            return DEFAULT_INVITE_TOKEN_SECRET;
        }

        return secret;
    }

    // [be] 영은 260523 1120 | 사용자/운영자가 식별할 더치페이 주문번호를 중복 없이 생성한다
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
