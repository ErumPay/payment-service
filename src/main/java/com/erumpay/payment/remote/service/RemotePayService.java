package com.erumpay.payment.remote.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.dao.RemotePayRequestRepository;
import com.erumpay.payment.remote.domain.dto.RemotePayCoreCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayCreateResponse;
import com.erumpay.payment.remote.domain.dto.RemotePayDraftCreateRequest;
import com.erumpay.payment.remote.domain.dto.RemotePayExpireBatchResponse;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity;
import com.erumpay.payment.remote.domain.entity.RemotePayRequestEntity.RemotePayStatus;
import com.erumpay.payment.remote.domain.dto.RemotePayTargetAssignRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class RemotePayService {

    private final RemotePayRequestRepository remotePayRequestRepository;
    private final CoreRepository coreRepository;
    private final RemotePayFriendValidator remotePayFriendValidator;
    private final RemotePaySseService remotePaySseService;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.remote-pay.expires-after-minutes:30}")
    private long expiresAfterMinutes;

    // [be] 영은 260527 1000 | 원격결제 요청 생성 - 초기 테스트/직접 생성용 흐름이다.
    // [be] 영은 260528 1040 | 최종 B안의 기본 진입점은 Core /payment/prepare이며, 이 메서드는 payment_id 없이 요청만 만들 때 사용한다.
    // [be] 영은 260527 1000 | 친구 검증은 외부 호출이므로 DB 트랜잭션 밖에서 먼저 처리해 원격 지연이 DB 락으로 이어지지 않게 한다.
    public RemotePayCreateResponse createRequest(Long requesterUserId, RemotePayCreateRequest request) {
        log.info("/api/v1/remote-pay/requests Service");

        if (requesterUserId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        remotePayFriendValidator.validate(requesterUserId, request.getTarget_user_id());

        RemotePayCreateResponse response = transactionTemplate.execute(status -> savePendingRequest(requesterUserId, request));
        publishRequestUpdated(response.getRequest_id(), "REQUEST_CREATED", response);
        return response;
    }

    // [be] 영은 260528 1010 | Core /payment/prepare에서 REMOTE 선택 시 내부 호출하는 원격결제 요청 생성 흐름이다.
    // [be] 영은 260528 1010 | 결제 주문 생성과 상태 관리는 Core가 담당하므로, remote-pay는 전달받은 payment_id에 request_id를 연결한다.
    // [be] 영은 260604 1910 | payment.userId는 대리결제자(targetUserId)이거나 아직 비어 있어야 한다.
    // 요청자 userId로 먼저 배정하면 대리결제자가 실제 결제를 진행할 때 소유자 검증이 꼬일 수 있다.
    public RemotePayCreateResponse createRequestFromCore(Long requesterUserId, RemotePayCoreCreateRequest request) {
        log.info("/internal/v1/remote-pay/requests Service");

        if (requesterUserId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        CoreEntity payment = coreRepository.findById(request.getPayment_id())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        remotePayFriendValidator.validate(requesterUserId, request.getTarget_user_id());

        RemotePayCreateResponse response = transactionTemplate.execute(
                status -> savePendingRequestFromCore(requesterUserId, request.getTarget_user_id(), payment,
                        request.getDescription()));
        publishRequestUpdated(response.getRequest_id(), "REQUEST_CREATED", response);
        return response;
    }

    public RemotePayCreateResponse createDraftFromCore(Long requesterUserId, RemotePayDraftCreateRequest request) {
        log.info("/internal/v1/remote-pay/requests/draft Service");

        if (requesterUserId == null || request == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        CoreEntity sourcePayment = coreRepository.findById(request.getSource_payment_id())
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        RemotePayCreateResponse response = transactionTemplate.execute(
                status -> saveDraftRequestFromCore(requesterUserId, sourcePayment, request.getDescription()));
        publishRequestUpdated(response.getRequest_id(), "REQUEST_DRAFT_CREATED", response);
        return response;
    }

    // [be] 영은 260528 1015 | 같은 모듈 내부에서 CoreService가 HTTP 없이 직접 호출할 때 쓰는 진입점이다.
    // [be] 영은 260528 1015 | 반환된 request_id를 Core가 prepare 응답의 remoteRequestId로 내려주면 프론트/알림이 같은 요청을 추적할 수 있다.
    public RemotePayCreateResponse createRequestFromCore(
            Long requesterUserId,
            Long targetUserId,
            CoreEntity payment,
            String description) {
        if (requesterUserId == null || targetUserId == null || payment == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        remotePayFriendValidator.validate(requesterUserId, targetUserId);

        RemotePayCreateResponse response = transactionTemplate.execute(
                status -> savePendingRequestFromCore(requesterUserId, targetUserId, payment, description));
        publishRequestUpdated(response.getRequest_id(), "REQUEST_CREATED", response);
        return response;
    }

    @Transactional(readOnly = true)
    public RemotePayCreateResponse getRequest(Long userId, Long requestId) {
        log.info("/api/v1/remote-pay/requests/{} Service", requestId);

        if (userId == null || requestId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        RemotePayRequestEntity request = remotePayRequestRepository.findDetailByIdAndUserId(requestId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
        return RemotePayCreateResponse.fromEntity(request);
    }

    @Transactional
    public RemotePayCreateResponse assignTarget(Long requesterUserId, Long requestId, RemotePayTargetAssignRequest targetRequest) {
        log.info("/api/v1/remote-pay/requests/{}/target Service", requestId);

        if (requesterUserId == null || targetRequest == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        remotePayFriendValidator.validate(requesterUserId, targetRequest.getTarget_user_id());

        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.assignTarget(requesterUserId, targetRequest.getTarget_user_id(), LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        RemotePayCreateResponse response = RemotePayCreateResponse.fromEntity(request);
        publishAfterCommit(response.getRequest_id(), "TARGET_ASSIGNED", response);
        return response;
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
                List.of(RemotePayStatus.DRAFT, RemotePayStatus.PENDING))
                .stream()
                .map(RemotePayCreateResponse::fromEntity)
                .toList();
    }

    // [be] 영은 260527 1430 | A안에서 쓰던 결제 주문 사후 연결 흐름이다.
    // [be] 영은 260528 1040 | B안에서는 요청 생성 시점에 payment_id를 함께 묶으므로 Core 쪽 전환이 끝나면 제거 대상이다.
    @Transactional
    public RemotePayCreateResponse connectPaymentForPrepare(Long targetUserId, Long requestId, CoreEntity payment) {
        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.assignPayment(targetUserId, payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        RemotePayCreateResponse response = RemotePayCreateResponse.fromEntity(request);
        publishAfterCommit(response.getRequest_id(), "PAYMENT_CONNECTED", response);
        return response;
    }

    public RemotePayCreateResponse connectPaymentForPrepare(Long targetUserId, Long requestId, Long payerPaymentId) {
        if (targetUserId == null || requestId == null || payerPaymentId == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        CoreEntity payment = coreRepository.findById(payerPaymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));
        return connectPaymentForPrepare(targetUserId, requestId, payment);
    }

    // [be] 영은 260527 1040 | 요청받은 사람은 아직 PENDING인 원격결제 요청을 거절할 수 있다.
    // [be] 영은 260528 1040 | B안에서는 payment_id가 요청 생성 시점부터 존재하므로, 취소/거절 가능 여부는 payment_id가 아니라 요청 상태로 판단한다.
    @Transactional
    public RemotePayCreateResponse rejectRequest(Long targetUserId, Long requestId, String rejectReason) {
        log.info("/api/v1/remote-pay/requests/{}/reject Service", requestId);

        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.reject(targetUserId, normalizeDescription(rejectReason), LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        RemotePayCreateResponse response = RemotePayCreateResponse.fromEntity(request);
        publishAfterCommit(response.getRequest_id(), "REQUEST_REJECTED", response);
        return response;
    }

    // [be] 영은 260527 1050 | 요청자는 아직 PENDING인 원격결제 요청을 취소할 수 있다.
    // [be] 영은 260528 1040 | B안에서는 payment_id가 미리 연결되어도 결제 성공 전이면 요청 취소 자체는 request status 기준으로 처리한다.
    @Transactional
    public RemotePayCreateResponse cancelRequest(Long requesterUserId, Long requestId) {
        log.info("/api/v1/remote-pay/requests/{}/cancel Service", requestId);

        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.cancel(requesterUserId, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        RemotePayCreateResponse response = RemotePayCreateResponse.fromEntity(request);
        publishAfterCommit(response.getRequest_id(), "REQUEST_CANCELLED", response);
        return response;
    }

    // [be] 영은 260527 1450 | 실제 PG 결제가 성공한 뒤 Core가 호출해 원격결제 요청을 COMPLETED로 전환한다.
    // [be] 영은 260527 1450 | RemotePay는 카드 승인 자체를 수행하지 않고, 결제 결과에 따른 요청 상태만 관리한다.
    @Transactional
    public void completeByPayment(CoreEntity payment) {
        if (payment == null) {
            return;
        }

        RemotePayRequestEntity request = getRequestForPayment(payment);
        try {
            request.complete(payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
        publishAfterCommit(request.getRequest_id(), "PAYMENT_COMPLETED", RemotePayCreateResponse.fromEntity(request));
    }

    // [be] 영은 260527 1440 | Core가 PG 요청 직전에 호출해 취소/거절/만료된 원격결제가 결제되지 않도록 막는다.
    // [be] 영은 260527 1440 | prepare 이후 request 전 사이에 상태가 바뀔 수 있으므로 최종 결제 직전에 한 번 더 검증한다.
    @Transactional
    public void validatePaymentCanBeRequested(CoreEntity payment) {
        if (payment == null || payment.getPayment_type() != CoreEntity.PaymentType.REMOTE) {
            return;
        }

        RemotePayRequestEntity request = getRequestForPayment(payment);
        try {
            request.requirePayable(payment, LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
    }

    // [be] 영은 260528 1120 | 원격결제 만료 배치 - 만료 시간이 지난 PENDING 요청을 EXPIRED로 확정한다.
    // [be] 영은 260528 1120 | 한 건 실패가 전체 배치를 롤백하지 않도록 요청 단위로 예외를 격리하고 실패 id를 응답에 남긴다.
    public RemotePayExpireBatchResponse expirePendingRequests() {
        log.info("/internal/v1/remote-pay/expire-batch Service");

        LocalDateTime now = LocalDateTime.now();
        List<Long> targetIds = remotePayRequestRepository.findExpiredTargetIds(
                RemotePayStatus.PENDING,
                now);
        List<RemotePayExpireBatchResponse.ExpiredRequest> expiredRequests = new ArrayList<>();
        List<Long> failedRequestIds = new ArrayList<>();

        for (Long requestId : targetIds) {
            try {
                RemotePayCreateResponse response = transactionTemplate.execute(
                        status -> expireSingleRequest(requestId, now));
                expiredRequests.add(toExpiredRequest(response));
                publishRequestUpdated(response.getRequest_id(), "REQUEST_EXPIRED", response);
            } catch (RuntimeException e) {
                failedRequestIds.add(requestId);
                log.warn("RemotePay expire handling failed. request_id={}", requestId, e);
            }
        }

        return RemotePayExpireBatchResponse.builder()
                .expired_count(expiredRequests.size())
                .failed_count(failedRequestIds.size())
                .failed_request_ids(failedRequestIds)
                .expired_requests(expiredRequests)
                .build();
    }

    // [be] 영은 260527 1005 | payment_id 없이 payment_remote_requests 행만 생성하는 보조 흐름이다.
    // [be] 영은 260528 1040 | B안의 운영 흐름에서는 savePendingRequestFromCore가 payment_orders와 원격요청을 같이 묶는다.
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

    private RemotePayCreateResponse saveDraftRequestFromCore(
            Long requesterUserId,
            CoreEntity sourcePayment,
            String description) {
        RemotePayRequestEntity existing = remotePayRequestRepository
                .findBySourcePaymentIdForUpdate(sourcePayment.getPaymentId())
                .orElse(null);
        if (existing != null) {
            if (!requesterUserId.equals(existing.getRequester_user_id())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            return RemotePayCreateResponse.fromEntity(existing);
        }

        if (sourcePayment.getPaymentId() == null
                || sourcePayment.getAmount() == null
                || sourcePayment.getAmount() <= 0
                || sourcePayment.getChannel_type() != CoreEntity.ChannelType.ONLINE) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        RemotePayRequestEntity remoteRequest;
        try {
            remoteRequest = RemotePayRequestEntity.draftFromCore(
                    requesterUserId,
                    sourcePayment.getPaymentId(),
                    sourcePayment.getAmount(),
                    normalizeDescription(description),
                    now.plusMinutes(expiresAfterMinutes),
                    now);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayCreateResponse.fromEntity(remotePayRequestRepository.save(remoteRequest));
    }

    // [be] 영은 260528 1020 | B안 원격결제 요청 저장 로직이다. payment_orders는 이미 존재하므로 새 결제 주문을 만들지 않는다.
    // [be] 영은 260528 1020 | request_id와 payment_id를 처음부터 묶어두면 완료/취소/만료 알림에서 같은 원격요청을 안정적으로 추적할 수 있다.
    private RemotePayCreateResponse savePendingRequestFromCore(
            Long requesterUserId,
            Long targetUserId,
            CoreEntity payment,
            String description) {
        RemotePayRequestEntity existing = remotePayRequestRepository.findByPaymentIdForUpdate(payment.getPaymentId())
                .orElse(null);
        if (existing != null) {
            if (!requesterUserId.equals(existing.getRequester_user_id())
                    || !targetUserId.equals(existing.getTarget_user_id())) {
                throw new CustomException(ErrorCode.BAD_REQUEST);
            }
            return RemotePayCreateResponse.fromEntity(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        RemotePayRequestEntity remoteRequest;
        try {
            remoteRequest = RemotePayRequestEntity.pendingFromCore(
                    requesterUserId,
                    targetUserId,
                    payment,
                    normalizeDescription(description),
                    now.plusMinutes(expiresAfterMinutes),
                    now);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }

        return RemotePayCreateResponse.fromEntity(remotePayRequestRepository.save(remoteRequest));
    }

    private RemotePayCreateResponse expireSingleRequest(Long requestId, LocalDateTime now) {
        RemotePayRequestEntity request = getRequestForUpdate(requestId);
        try {
            request.expire(now);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new CustomException(ErrorCode.BAD_REQUEST, e);
        }
        return RemotePayCreateResponse.fromEntity(request);
    }

    private RemotePayExpireBatchResponse.ExpiredRequest toExpiredRequest(RemotePayCreateResponse request) {
        return RemotePayExpireBatchResponse.ExpiredRequest.builder()
                .request_id(request.getRequest_id())
                .requester_user_id(request.getRequester_user_id())
                .target_user_id(request.getTarget_user_id())
                .payment_id(request.getPayment_id())
                .build();
    }

    // [be] 영은 260528 1110 | DB 커밋 성공 이후에만 SSE를 발행해 화면이 롤백된 상태를 먼저 보지 않게 한다.
    private void publishAfterCommit(Long requestId, String eventType, RemotePayCreateResponse response) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishRequestUpdated(requestId, eventType, response);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishRequestUpdated(requestId, eventType, response);
            }
        });
    }

    private void publishRequestUpdated(Long requestId, String eventType, RemotePayCreateResponse response) {
        remotePaySseService.publishRequestUpdated(requestId, eventType, response);
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

    private RemotePayRequestEntity getRequestForPayment(CoreEntity payment) {
        if (payment == null || payment.getPaymentId() == null) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        if (payment.getRemote_request_id() != null) {
            return getRequestForUpdate(payment.getRemote_request_id());
        }

        return remotePayRequestRepository.findByPaymentIdForUpdate(payment.getPaymentId())
                .orElseThrow(() -> new CustomException(ErrorCode.BAD_REQUEST));
    }

}
