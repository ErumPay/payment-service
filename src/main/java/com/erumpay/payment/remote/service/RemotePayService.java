package com.erumpay.payment.remote.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.dao.RemotePayRequestRepository;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity.RemotePayStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemotePayService {

    private final RemotePayRequestRepository remotePayRequestRepository;
    private final RemotePayFriendValidator remotePayFriendValidator;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.remote-pay.expires-after-minutes:30}")
    private long expiresAfterMinutes;

    // [be] 영은 260527 1000 | 원격결제 요청 생성 - 요청자는 결제를 직접 만들지 않고 target에게 결제 요청만 남긴다.
    // [be] 영은 260527 1000 | 친구 검증은 외부 호출이므로 DB 트랜잭션 밖에서 먼저 처리해 원격 지연이 DB 락으로 이어지지 않게 한다.
    public RemotePayCreateResponse createRequest(Long requesterUserId, RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Service");

        if (requesterUserId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        remotePayFriendValidator.validate(requesterUserId, request.getTarget_user_id());

        return transactionTemplate.execute(status -> savePendingRequest(requesterUserId, request));
    }

    // [be] 영은 260527 1010 | 요청 상세 조회 - 알림 클릭/상세 화면 복원 시 requester 또는 target만 현재 상태를 확인한다.
    // [be] 영은 260527 1010 | payment_id도 같이 내려줘서 결제 준비 이후에는 어떤 payment_orders와 연결됐는지 추적할 수 있게 한다.
    @Transactional(readOnly = true)
    public RemotePayCreateResponse getRequest(Long userId, Long requestId) {
        log.info("/api/v1/remote-pay/requests/{} Service", requestId);

        if (userId == null || requestId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        RemotePayRequestEntity request = remotePayRequestRepository.findDetailById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        ensureParticipant(userId, request);

        return RemotePayCreateResponse.fromEntity(request);
    }

    // [be] 영은 260527 1020 | 진행 중 요청 목록 조회 - 홈/알림함에서 아직 PENDING인 원격결제 요청만 보여주기 위한 API다.
    // [be] 영은 260527 1020 | 같은 사용자가 요청자/대상자 양쪽 역할을 가질 수 있어 두 컬럼을 모두 조회한다.
    @Transactional(readOnly = true)
    public List<RemotePayCreateResponse> getActiveRequests(Long userId) {
        log.info("/api/v1/remote-pay/requests/active Service");

        if (userId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return remotePayRequestRepository.findRequestsByUserIdAndStatuses(
                userId,
                List.of(RemotePayStatus.PENDING))
                .stream()
                .map(RemotePayCreateResponse::fromEntity)
                .toList();
    }

    // [be] 영은 260527 1430 | Core /payment/prepare가 생성/준비한 payment_orders를 원격결제 요청과 연결한다.
    // [be] 영은 260527 1430 | 결제 금액과 결제자(userId)가 원격결제 요청의 amount/target_user_id와 맞는지 검증해 다른 요청과 섞이지 않게 한다.
    @Transactional
    public void connectPaymentForPrepare(Long targetUserId, Long requestId, CoreEntity payment) {
        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.assignPayment(targetUserId, payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
    }

    // [be] 영은 260527 1040 | 요청받은 사람은 결제 준비 전까지만 거절할 수 있다.
    // [be] 영은 260527 1040 | payment_id가 연결된 뒤에는 결제가 시작된 상태라 요청 거절이 아니라 결제 취소/만료 정책으로 넘어간다.
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

    // [be] 영은 260527 1050 | 요청자는 결제 준비 전까지만 요청을 취소할 수 있다.
    // [be] 영은 260527 1050 | target이 이미 결제 화면에 진입해 payment_order가 연결되면 데이터 정합성을 위해 요청 취소를 막는다.
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

    // [be] 영은 260527 1450 | 실제 PG 결제가 성공한 뒤 Core가 호출해 원격결제 요청을 COMPLETED로 전환한다.
    // [be] 영은 260527 1450 | RemotePay는 카드 승인 자체를 수행하지 않고, 결제 결과에 따른 요청 상태만 관리한다.
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

    // [be] 영은 260527 1440 | Core가 PG 요청 직전에 호출해 취소/거절/만료된 원격결제가 결제되지 않도록 막는다.
    // [be] 영은 260527 1440 | prepare 이후 request 전 사이에 상태가 바뀔 수 있으므로 최종 결제 직전에 한 번 더 검증한다.
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

    // [be] 영은 260527 1005 | 원격결제 도메인은 payment_remote_requests 행만 생성한다.
    // [be] 영은 260527 1005 | payment_orders 생성/상태 변경은 Core 책임으로 분리해 기존 결제 흐름을 유지한다.
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

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }

    // [be] 영은 260527 1030 | 요청 상태 변경 시 같은 request_id를 비관적 락으로 잡아 중복 취소/거절/완료 전이를 막는다.
    private RemotePayRequestEntity getRequestForUpdate(Long requestId) {
        if (requestId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        return remotePayRequestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }

    private void ensureParticipant(Long userId, RemotePayRequestEntity request) {
        if (!userId.equals(request.getRequester_user_id()) && !userId.equals(request.getTarget_user_id())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
