package com.erumpay.payment.core.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.erumpay.payment.core.client.pg.dto.PgAuthPayResponse;
import com.erumpay.payment.core.client.pg.dto.PgSplitPayResponse;
import com.erumpay.payment.core.dao.CardDetailRepository;
import com.erumpay.payment.core.dao.CancelRepository;
import com.erumpay.payment.core.dao.CoreRepository;
import com.erumpay.payment.core.dao.EventRepository;
import com.erumpay.payment.core.domain.dto.PaidCardRequest;
import com.erumpay.payment.core.domain.entity.CancelEntity;
import com.erumpay.payment.core.domain.entity.CardDetailEntity;
import com.erumpay.payment.core.domain.entity.CardDetailEntity.CardStatus;
import com.erumpay.payment.core.domain.entity.CoreEntity;
import com.erumpay.payment.core.domain.entity.EventEntity;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorePgPaymentPersistenceService {

    private static final String BILLING_KEY_LOOKUP_FAILED = "BILLING_KEY_LOOKUP_FAILED";

    private final CardDetailRepository cardDetailRepository;
    private final CancelRepository cancelRepository;
    private final CoreRepository coreRepository;
    private final EventRepository eventRepository;

    // [be] 다윤 260607 05:00 | CREATED 이벤트 저장 메서드
    @Transactional
    public void saveCreatedEvent(Long paymentId, EventEntity.ActorType actorType) {
        saveStatusEvent(paymentId, EventEntity.EventType.CREATED, actorType, LocalDateTime.now());
    }

    // [be] 다윤 260607 05:00 | PAY_PENDING 이벤트 저장 메서드
    @Transactional
    public void savePayPendingEvent(Long paymentId, EventEntity.ActorType actorType) {
        saveStatusEvent(paymentId, EventEntity.EventType.PAY_PENDING, actorType, LocalDateTime.now());
    }

    // [be] 다윤 260607 05:00 | CANCEL_REQUESTED 상태와 이벤트 저장 메서드
    @Transactional
    public void markCancelRequestedAndSaveEvent(Long paymentId, EventEntity.ActorType actorType) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.CANCEL_REQUESTED)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        payment.cancelRequestedStatusUpdatePayment(now);
        saveStatusEvent(payment.getPaymentId(), EventEntity.EventType.CANCEL_REQUESTED, actorType, now);
    }

    // [be] 다윤 260607 05:00 | CANCELED 상태와 이벤트 저장 메서드
    @Transactional
    public void markCanceledAndSaveEvent(
            Long paymentId,
            EventEntity.ActorType actorType,
            LocalDateTime canceledAt) {
        markCanceledAndSaveEvent(paymentId, actorType, canceledAt, null, null);
    }

    @Transactional
    public void markCanceledAndSaveEvent(
            Long paymentId,
            EventEntity.ActorType actorType,
            LocalDateTime canceledAt,
            Long pgTxnId,
            Long pgGroupId) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.CANCELED)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.voidedStatusUpdatePayment(canceledAt);
        saveEventOrThrow(EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgTxnId)
                .pg_group_id(pgGroupId)
                .event_type(EventEntity.EventType.CANCELED)
                .actor_type(actorType)
                .created_at(canceledAt)
                .build());
    }

    // [be] 다윤 260607 05:00 | PG_PENDING 상태와 이벤트 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPgPendingAndSaveEvent(Long paymentId, EventEntity.ActorType actorType) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.PG_PENDING)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        payment.pgPendingStatusUpdatePayment(now);
        saveStatusEvent(payment.getPaymentId(), EventEntity.EventType.PG_PENDING, actorType, now);
    }

    // [be] 다윤 260601 20:00 | 결제 실패 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.FAILED)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.failedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .event_type(EventEntity.EventType.FAILED)
                .fail_code(extractFailCode(pgResponse))
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        saveEventOrThrow(savedEvent);
    }

    // [be] 다윤 260601 20:00 | 결제 성공 내역 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPaidAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse, String strategyType) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.PAID)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        payment.updateStrategyType(strategyType, now);
        if (pgResponse != null && pgResponse.getPgGroupId() != null) {
            payment.updatePgGroupId(pgResponse.getPgGroupId(), now);
        }
        payment.paidStatusUpdatePayment(now);

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .event_type(EventEntity.EventType.PAID)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        saveEventOrThrow(savedEvent);
    }

    // [be] 다윤 260601 20:00 | 결제 성공 카드 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CardDetailEntity savePaidCardDetail(PaidCardRequest paidCard) {
        if (paidCard == null || paidCard.getPaymentId() == null || paidCard.getPgTxnId() == null) {
            throw new CustomException(ErrorCode.PAYMENT_CARD_DETAIL_SAVE_FAILED);
        }

        try {
            return cardDetailRepository.findByPaymentIdAndPgTxnId(paidCard.getPaymentId(), paidCard.getPgTxnId())
                    .orElseGet(() -> cardDetailRepository.save(toCardDetailEntity(paidCard)));
        } catch (CustomException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.PAYMENT_CARD_DETAIL_SAVE_FAILED, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCardCancelRequested(Long paymentCardId) {
        CardDetailEntity cardDetail = findCardDetailOrThrow(paymentCardId);
        cardDetail.markCancelRequested();
    }

    // [be] 다윤 260607 05:00 | 단건 카드 취소 요청 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelRequestedHistory(Long paymentId, CardDetailEntity cardDetail) {
        LocalDateTime now = LocalDateTime.now();
        saveCancelHistoryOrThrow(CancelEntity.builder()
                .payment_id(paymentId)
                .amount(cardDetail.getPaid_amount())
                .pg_txn_id(cardDetail.getPg_txn_id())
                .cancel_status(CancelEntity.CancelStatus.REQUESTED)
                .created_at(now)
                .build());
    }

    // [be] 다윤 260607 05:00 | 분할결제 카드 취소 요청 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelRequestedHistories(Long paymentId, List<CardDetailEntity> cardDetails) {
        saveCancelRequestedHistories(paymentId, null, cardDetails);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelRequestedHistories(Long paymentId, Long pgGroupId, List<CardDetailEntity> cardDetails) {
        LocalDateTime now = LocalDateTime.now();
        saveCancelHistoriesOrThrow(cardDetails.stream()
                .map(cardDetail -> CancelEntity.builder()
                        .payment_id(paymentId)
                        .amount(cardDetail.getPaid_amount())
                        .pg_txn_id(cardDetail.getPg_txn_id())
                        .pg_group_id(pgGroupId)
                        .cancel_status(CancelEntity.CancelStatus.REQUESTED)
                        .created_at(now)
                        .build())
                .toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCardCanceled(Long paymentCardId, LocalDateTime canceledAt) {
        CardDetailEntity cardDetail = findCardDetailOrThrow(paymentCardId);
        cardDetail.markCanceled(canceledAt);
    }

    // [be] 다윤 260607 05:00 | 단건 카드 취소 성공 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCanceledHistory(
            Long paymentId,
            CardDetailEntity cardDetail,
            PgAuthPayResponse pgResponse,
            LocalDateTime canceledAt) {
        saveCancelHistoryOrThrow(CancelEntity.builder()
                .payment_id(paymentId)
                .amount(cardDetail.getPaid_amount())
                .pg_txn_id(cardDetail.getPg_txn_id())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .pg_cancel_approval_num(pgResponse == null ? null : pgResponse.getPgApprovalNumber())
                .cancel_status(CancelEntity.CancelStatus.CANCELLED)
                .created_at(canceledAt)
                .canceled_at(canceledAt)
                .build());
    }

    // [be] 다윤 260607 05:00 | 분할결제 카드 취소 성공 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCanceledHistories(
            Long paymentId,
            List<CardDetailEntity> cardDetails,
            PgSplitPayResponse pgResponse,
            LocalDateTime canceledAt) {
        Map<Long, PgSplitPayResponse.Item> itemByOriginalTxnId = pgResponse == null || pgResponse.getItems() == null
                ? Map.of()
                : pgResponse.getItems().stream()
                        .filter(item -> item.getOriginalTxnId() != null)
                        .collect(Collectors.toMap(
                                PgSplitPayResponse.Item::getOriginalTxnId,
                                Function.identity(),
                                (first, second) -> {
                                    log.warn(
                                            "duplicate pg split cancel item found. originalTxnId={}, firstItem={}, secondItem={}. using second item",
                                            first.getOriginalTxnId(),
                                            summarizePgSplitItem(first),
                                            summarizePgSplitItem(second));
                                    return second;
                                }));

        saveCancelHistoriesOrThrow(cardDetails.stream()
                .map(cardDetail -> {
                    PgSplitPayResponse.Item item = itemByOriginalTxnId.get(cardDetail.getPg_txn_id());
                    return CancelEntity.builder()
                            .payment_id(paymentId)
                            .amount(cardDetail.getPaid_amount())
                            .pg_txn_id(cardDetail.getPg_txn_id())
                            .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                            .pg_cancel_approval_num(item == null ? null : item.getPgApprovalNumber())
                            .cancel_status(CancelEntity.CancelStatus.CANCELLED)
                            .created_at(canceledAt)
                            .canceled_at(canceledAt)
                            .build();
                })
                .toList());
    }

    private String summarizePgSplitItem(PgSplitPayResponse.Item item) {
        if (item == null) {
            return "null";
        }

        return "Item{pgTxnId=" + item.getPgTxnId()
                + ", pgGroupId=" + item.getPgGroupId()
                + ", splitSeq=" + item.getSplitSeq()
                + ", originalTxnId=" + item.getOriginalTxnId()
                + ", payPaymentId=" + item.getPayPaymentId()
                + ", merchantId=" + item.getMerchantId()
                + ", txnType=" + item.getTxnType()
                + ", status=" + item.getStatus()
                + ", amount=" + item.getAmount()
                + ", pgApprovalNumber=" + item.getPgApprovalNumber()
                + ", cardApprovalNumber=" + item.getCardApprovalNumber()
                + ", rejectReason=" + item.getRejectReason()
                + ", failureCode=" + item.getFailureCode()
                + ", failureReason=" + item.getFailureReason()
                + ", failureMessage=" + item.getFailureMessage()
                + ", approvedAt=" + item.getApprovedAt()
                + ", processedAt=" + item.getProcessedAt()
                + "}";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCardsCanceled(List<Long> paymentCardIds, LocalDateTime canceledAt) {
        if (paymentCardIds == null || paymentCardIds.isEmpty() || canceledAt == null) {
            throw new CustomException(ErrorCode.CANCELED_CARD_INVALID);
        }

        List<Long> distinctPaymentCardIds = paymentCardIds.stream()
                .distinct()
                .toList();
        List<CardDetailEntity> cardDetails = cardDetailRepository.findAllById(distinctPaymentCardIds);
        if (cardDetails.size() != distinctPaymentCardIds.size()) {
            throw new CustomException(ErrorCode.CANCELED_CARD_INVALID);
        }

        cardDetails.forEach(cardDetail -> cardDetail.markCanceled(canceledAt));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCardCancelFailed(Long paymentCardId) {
        CardDetailEntity cardDetail = findCardDetailOrThrow(paymentCardId);
        cardDetail.markCancelFailed();
    }

    // [be] 다윤 260607 05:00 | 단건 카드 취소 실패 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelFailedHistory(
            Long paymentId,
            CardDetailEntity cardDetail,
            PgAuthPayResponse pgResponse,
            RuntimeException exception) {
        saveCancelHistoryOrThrow(CancelEntity.builder()
                .payment_id(paymentId)
                .amount(cardDetail.getPaid_amount())
                .pg_txn_id(cardDetail.getPg_txn_id())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .fail_code(resolveCancelFailCode(pgResponse, exception))
                .cancel_status(CancelEntity.CancelStatus.FAILED)
                .created_at(LocalDateTime.now())
                .build());
    }

    // [be] 다윤 260607 05:00 | 분할결제 카드 취소 실패 이력 저장 메서드
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelFailedHistories(
            Long paymentId,
            List<CardDetailEntity> cardDetails,
            PgSplitPayResponse pgResponse,
            RuntimeException exception) {
        saveCancelFailedHistories(paymentId, null, cardDetails, pgResponse, exception);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCancelFailedHistories(
            Long paymentId,
            Long pgGroupId,
            List<CardDetailEntity> cardDetails,
            PgSplitPayResponse pgResponse,
            RuntimeException exception) {
        String failCode = resolveCancelFailCode(pgResponse, exception);
        LocalDateTime now = LocalDateTime.now();
        saveCancelHistoriesOrThrow(cardDetails.stream()
                .map(cardDetail -> CancelEntity.builder()
                        .payment_id(paymentId)
                        .amount(cardDetail.getPaid_amount())
                        .pg_txn_id(cardDetail.getPg_txn_id())
                        .pg_group_id(pgResponse == null ? pgGroupId : pgResponse.getPgGroupId())
                        .fail_code(failCode)
                        .cancel_status(CancelEntity.CancelStatus.FAILED)
                        .created_at(now)
                        .build())
                .toList());
    }

    // [be] 다윤 260601 20:00 | 가승인 성공 원장 기록
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAuthorizedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.AUTHORIZED)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.authorizedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .event_type(EventEntity.EventType.AUTHORIZED)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        saveEventOrThrow(savedEvent);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markVoidedAndSaveEvent(Long paymentId, PgAuthPayResponse pgResponse) {
        if (hasStatusEvent(paymentId, EventEntity.EventType.VOIDED)) {
            return;
        }

        CoreEntity payment = coreRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(ErrorCode.PAY_NOT_FOUND));

        payment.authVoidedStatusUpdatePayment(LocalDateTime.now());

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(payment.getPaymentId())
                .pg_txn_id(pgResponse == null ? null : pgResponse.getPgTxnId())
                .pg_group_id(pgResponse == null ? null : pgResponse.getPgGroupId())
                .event_type(EventEntity.EventType.VOIDED)
                .actor_type(EventEntity.ActorType.PG)
                .created_at(LocalDateTime.now())
                .build();

        saveEventOrThrow(savedEvent);
    }

    private String extractFailCode(PgAuthPayResponse pgResponse) {
        if (pgResponse == null) {
            return null;
        }

        String failureCode = pgResponse.getFailureCode();
        if (BILLING_KEY_LOOKUP_FAILED.equals(failureCode)) {
            return failureCode;
        }
        return null;
    }

    // [be] 다윤 260607 05:00 | 단건 취소 실패 코드 추출 메서드
    private String resolveCancelFailCode(PgAuthPayResponse pgResponse, RuntimeException exception) {
        if (pgResponse != null) {
            if (pgResponse.getFailureCode() != null && !pgResponse.getFailureCode().isBlank()) {
                return pgResponse.getFailureCode();
            }
            if (pgResponse.getRejectReason() != null && !pgResponse.getRejectReason().isBlank()) {
                return pgResponse.getRejectReason();
            }
        }

        return resolveExceptionFailCode(exception);
    }

    // [be] 다윤 260607 05:00 | 분할취소 실패 코드 추출 메서드
    private String resolveCancelFailCode(PgSplitPayResponse pgResponse, RuntimeException exception) {
        if (pgResponse != null) {
            if (pgResponse.getFailureCode() != null && !pgResponse.getFailureCode().isBlank()) {
                return pgResponse.getFailureCode();
            }
            if (pgResponse.getFailureReason() != null && !pgResponse.getFailureReason().isBlank()) {
                return pgResponse.getFailureReason();
            }
        }

        return resolveExceptionFailCode(exception);
    }

    private String resolveExceptionFailCode(RuntimeException exception) {
        if (exception instanceof CustomException customException) {
            return customException.getErrorCode().getCode();
        }
        return exception == null ? null : exception.getClass().getSimpleName();
    }

    // [be] 다윤 260607 05:00 | 결제 상태 이벤트 중복 확인 메서드
    private boolean hasStatusEvent(Long paymentId, EventEntity.EventType eventType) {
        return eventRepository.existsByPaymentIdAndEventType(paymentId, eventType);
    }

    private void saveStatusEvent(
            Long paymentId,
            EventEntity.EventType eventType,
            EventEntity.ActorType actorType,
            LocalDateTime createdAt) {
        if (hasStatusEvent(paymentId, eventType)) {
            return;
        }

        EventEntity savedEvent = EventEntity.builder()
                .payment_id(paymentId)
                .event_type(eventType)
                .actor_type(actorType)
                .created_at(createdAt)
                .build();

        saveEventOrThrow(savedEvent);
    }

    private void saveEventOrThrow(EventEntity event) {
        try {
            eventRepository.save(event);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.PAYMENT_EVENT_SAVE_FAILED, e);
        }
    }

    // [be] 다윤 260607 05:00 | 결제 취소 이력 단건 저장 메서드
    private void saveCancelHistoryOrThrow(CancelEntity cancelHistory) {
        try {
            cancelRepository.save(cancelHistory);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_HISTORY_SAVE_FAILED, e);
        }
    }

    // [be] 다윤 260607 05:00 | 결제 취소 이력 일괄 저장 메서드
    private void saveCancelHistoriesOrThrow(List<CancelEntity> cancelHistories) {
        try {
            cancelRepository.saveAll(cancelHistories);
        } catch (RuntimeException e) {
            throw new CustomException(ErrorCode.PAYMENT_CANCEL_HISTORY_SAVE_FAILED, e);
        }
    }

    private CardDetailEntity findCardDetailOrThrow(Long paymentCardId) {
        return cardDetailRepository.findById(paymentCardId)
                .orElseThrow(() -> new CustomException(ErrorCode.CANCELED_CARD_INVALID));
    }

    private CardDetailEntity toCardDetailEntity(PaidCardRequest paidCard) {
        if (paidCard == null
                || paidCard.getPaymentId() == null
                || paidCard.getPgTxnId() == null
                || paidCard.getPgApprovalNum() == null
                || paidCard.getCardId() == null
                || paidCard.getMaskedNumber() == null
                || paidCard.getCardName() == null
                || paidCard.getPaidAmount() == null
                || paidCard.getTotalBenefitAmount() == null
                || paidCard.getPaidAt() == null) {
            throw new CustomException(ErrorCode.PAYMENT_CARD_DETAIL_SAVE_FAILED);
        }

        return CardDetailEntity.builder()
                .payment_id(paidCard.getPaymentId())
                .pg_txn_id(paidCard.getPgTxnId())
                .pg_approval_num(paidCard.getPgApprovalNum())
                .card_id(paidCard.getCardId())
                .masked_number(paidCard.getMaskedNumber())
                .card_name(paidCard.getCardName())
                .paid_amount(paidCard.getPaidAmount())
                .discount_amount(paidCard.getTotalBenefitAmount())
                .benefit_desc(paidCard.getBenefitDesc())
                .paid_at(paidCard.getPaidAt())
                .card_status(CardStatus.PAID)
                .build();
    }
}
