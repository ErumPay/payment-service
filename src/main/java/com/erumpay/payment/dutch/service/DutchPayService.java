package com.erumpay.payment.dutch.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
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
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.dutch.dao.DutchPayParticipantRepository;
import com.erumpay.payment.dutch.dao.DutchPaySessionRepository;
import com.erumpay.payment.dutch.domain.dto.DutchPayAmountRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayCreateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayHostAuthorizationResultRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteLinkResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayInviteRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantPaymentValidateResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPayParticipantsConfirmRequest;
import com.erumpay.payment.dutch.domain.dto.DutchPaySessionDetailResponse;
import com.erumpay.payment.dutch.domain.dto.DutchPaySplitMethodRequest;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity.ParticipantStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.SplitMethod;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DutchPayService {

    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_RANDOM_DIGITS = 10;
    private static final long INVITE_TOKEN_TTL_MILLIS = 3L * 60L * 60L * 1000L;
    private static final String INVITE_TOKEN_HMAC_ALGORITHM = "HmacSHA256";
    private static final String DEFAULT_INVITE_TOKEN_SECRET = "erumpay-local-dutch-invite-token-secret";

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
        dutchPayParticipantRepository.save(DutchPayParticipantEntity.host(savedSession, request.getHost_user_id(), now));

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

    @Transactional(readOnly = true)
    public DutchPaySessionDetailResponse getSession(Long userId, Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        ensureSessionMember(session, participants, userId);

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    @Transactional(readOnly = true)
    public List<DutchPaySessionDetailResponse> getActiveSessions(Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return dutchPaySessionRepository.findActiveSessionsByUserId(
                userId,
                List.of(DutchPayStatus.CREATED, DutchPayStatus.IN_PROGRESS)).stream()
                .map(session -> DutchPaySessionDetailResponse.fromEntity(
                        session,
                        getParticipants(session.getSession_id())))
                .toList();
    }

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
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }

            dutchPayParticipantRepository.save(
                    DutchPayParticipantEntity.invited(session, inviteeUserId, null, now));
        }

        return toDetailResponse(sessionId);
    }

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

    @Transactional
    public DutchPaySessionDetailResponse acceptInviteLink(Long userId, String inviteToken) {
        if (userId == null || inviteToken == null || inviteToken.isBlank()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        Long sessionId = parseSessionIdFromInviteToken(inviteToken);
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);

        if (userId.equals(session.getHost_user_id())
                || dutchPayParticipantRepository.existsBySessionIdAndUserId(sessionId, userId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        dutchPayParticipantRepository.save(
                DutchPayParticipantEntity.invited(session, userId, null, LocalDateTime.now()));

        return toDetailResponse(sessionId);
    }

    @Transactional
    public DutchPaySessionDetailResponse rejectInvite(Long userId, Long sessionId) {
        if (userId == null || sessionId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        if (userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        participant.reject(LocalDateTime.now());
        if (session.getSplit_method() == SplitMethod.CUSTOM) {
            recalculateHostCustomAmount(session, getParticipants(sessionId), LocalDateTime.now());
        }

        return toDetailResponse(sessionId);
    }

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
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        participants.forEach(participant -> participant.confirm(now));

        if (request != null && request.getSplit_method() != null && !request.getSplit_method().isBlank()) {
            SplitMethod splitMethod = parseSplitMethod(request.getSplit_method());
            session.changeSplitMethod(splitMethod, now);
            applySplitMethod(session, participants, now);
        }

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    @Transactional
    public DutchPaySessionDetailResponse updateSplitMethod(
            Long hostUserId,
            Long sessionId,
            DutchPaySplitMethodRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureHostInProgress(session, hostUserId);

        LocalDateTime now = LocalDateTime.now();
        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        SplitMethod splitMethod = parseSplitMethod(request.getSplit_method());
        session.changeSplitMethod(splitMethod, now);
        applySplitMethod(session, participants, now);

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
    }

    @Transactional
    public DutchPaySessionDetailResponse updateMyAmount(
            Long userId,
            Long sessionId,
            DutchPayAmountRequest request) {
        if (userId == null || sessionId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        ensureInProgress(session);
        if (session.getSplit_method() != SplitMethod.CUSTOM || userId.equals(session.getHost_user_id())) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        DutchPayParticipantEntity participant = dutchPayParticipantRepository
                .findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        participant.updateAmount(request.getAmount(), LocalDateTime.now());

        List<DutchPayParticipantEntity> participants = getParticipants(sessionId);
        recalculateHostCustomAmount(session, participants, LocalDateTime.now());

        return DutchPaySessionDetailResponse.fromEntity(session, participants);
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

    private void validateInviteRequest(
            Long hostUserId,
            Long sessionId,
            DutchPayInviteRequest request) {
        if (hostUserId == null
                || sessionId == null
                || request == null
                || request.getUser_ids() == null
                || request.getUser_ids().isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private DutchPaySessionEntity getSessionOrThrow(Long sessionId) {
        if (sessionId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return dutchPaySessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }

    private List<DutchPayParticipantEntity> getParticipants(Long sessionId) {
        return dutchPayParticipantRepository.findBySessionIdOrderByParticipantId(sessionId);
    }

    private DutchPaySessionDetailResponse toDetailResponse(Long sessionId) {
        DutchPaySessionEntity session = getSessionOrThrow(sessionId);
        return DutchPaySessionDetailResponse.fromEntity(session, getParticipants(sessionId));
    }

    private void ensureHostInProgress(DutchPaySessionEntity session, Long hostUserId) {
        try {
            session.requireHost(hostUserId);
            session.requireInProgress();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void ensureInProgress(DutchPaySessionEntity session) {
        try {
            session.requireInProgress();
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void ensureSessionMember(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            Long userId) {
        if (userId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
        if (session.getHost_user_id().equals(userId)) {
            return;
        }

        boolean participant = participants.stream()
                .anyMatch(item -> item.getUser_id().equals(userId));
        if (!participant) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }

    private SplitMethod parseSplitMethod(String splitMethod) {
        try {
            return SplitMethod.valueOf(splitMethod.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

    private void applySplitMethod(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        // [be] 영은 260521 1621 | CUSTOM은 대표자에게 총액을 우선 배정하고 참여자 입력분만큼 차감
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

    private void distributeEqualAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        List<DutchPayParticipantEntity> payableParticipants = participants.stream()
                .filter(participant -> participant.getStatus() == ParticipantStatus.PENDING)
                .toList();
        if (payableParticipants.isEmpty()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
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

    private void recalculateHostCustomAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants,
            LocalDateTime now) {
        DutchPayParticipantEntity host = participants.stream()
                .filter(participant -> participant.getUser_id().equals(session.getHost_user_id()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));

        long memberAmountSum = participants.stream()
                .filter(participant -> !participant.getUser_id().equals(session.getHost_user_id()))
                .filter(participant -> participant.getStatus() == ParticipantStatus.PENDING)
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);
        if (memberAmountSum > session.getTotal_amount()) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        host.assignAmount(session.getTotal_amount() - memberAmountSum, now);
    }

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
                throw new IllegalArgumentException("Expired invite token");
            }

            return Long.valueOf(payloadParts[0]);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }
    }

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

    private String signInviteTokenPayload(String payload) {
        try {
            Mac mac = Mac.getInstance(INVITE_TOKEN_HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(resolveInviteTokenSecret().getBytes(StandardCharsets.UTF_8),
                    INVITE_TOKEN_HMAC_ALGORITHM));

            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private String resolveInviteTokenSecret() {
        String secret = System.getenv("DUTCH_INVITE_TOKEN_SECRET");
        if (secret == null || secret.isBlank()) {
            return DEFAULT_INVITE_TOKEN_SECRET;
        }

        return secret;
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
