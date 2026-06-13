package com.erumpay.payment.dutch.domain.dto;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity.ParticipantStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.SplitMethod;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DutchPaySessionDetailResponse {
    private static final Duration SESSION_EXPIRES_AFTER = Duration.ofMinutes(30);

    private Long session_id;
    private String dutch_order_no;
    private Long host_user_id;
    private Long merchant_id;
    private String merchant_name;
    private Long host_auth_payment_id;
    private Long total_amount;
    private Long remaining_amount;
    private String split_method;
    private String status;
    // [be] 영은 260610 | 프론트가 더치페이 홈에서 만료 시간을 직접 보조 판단할 수 있도록 생성/만료 시각을 내려준다.
    private LocalDateTime created_at;
    private LocalDateTime expires_at;
    private String session_progress_step;
    private List<DutchPayParticipantResponse> participants;

    public static DutchPaySessionDetailResponse fromEntity(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        Long assignedAmount = participants.stream()
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .reduce(0L, Long::sum);

        return DutchPaySessionDetailResponse.builder()
                .session_id(session.getSession_id())
                .dutch_order_no(session.getDutch_order_no())
                .host_user_id(session.getHost_user_id())
                .merchant_id(session.getMerchant_id())
                .merchant_name(session.getMerchant_name())
                .host_auth_payment_id(session.getHost_auth_payment_id())
                .total_amount(session.getTotal_amount())
                .remaining_amount(Math.max(0L, session.getTotal_amount() - assignedAmount))
                .split_method(session.getSplit_method().name())
                .status(session.getStatus().name())
                .created_at(session.getCreated_at())
                .expires_at(session.getCreated_at() == null ? null : session.getCreated_at().plus(SESSION_EXPIRES_AFTER))
                .participants(participants.stream()
                        .map(participant -> DutchPayParticipantResponse.fromEntity(participant, session.getHost_user_id()))
                        .toList())
                .session_progress_step(resolveSessionProgressStep(session, participants))
                .build();
    }

    private static String resolveSessionProgressStep(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        if (session.getStatus() == DutchPayStatus.CREATED) {
            return ProgressStep.GROUP_CREATED.name();
        }
        if (session.getStatus() == DutchPayStatus.FAILED) {
            return ProgressStep.FAILED.name();
        }
        if (session.getStatus() == DutchPayStatus.COMPLETED) {
            return ProgressStep.COMPLETED.name();
        }
        if (session.getStatus() == DutchPayStatus.CANCELED) {
            return ProgressStep.CANCELED.name();
        }
        if (session.getStatus() == DutchPayStatus.TIMEOUT_HANDLED) {
            return hostAmount(session, participants) > 0
                    ? ProgressStep.FINAL_PAYMENT_REQUIRED.name()
                    : ProgressStep.TIMEOUT_HANDLED.name();
        }
        if (participants == null || participants.size() <= 1) {
            return ProgressStep.GROUP_CREATED.name();
        }
        if (session.getParticipants_confirmed_at() == null
                && participants.stream().anyMatch(participant ->
                        participant.getStatus() == ParticipantStatus.INVITED
                                || participant.getStatus() == ParticipantStatus.JOINED)) {
            return ProgressStep.PARTICIPANT_CONFIRM.name();
        }
        if (session.getSplit_method() == SplitMethod.CUSTOM
                && participants.stream().anyMatch(participant ->
                        !isHost(session, participant)
                                && participant.getStatus() == ParticipantStatus.PENDING
                                && participant.getAmount() == null)) {
            return ProgressStep.AMOUNT_INPUT.name();
        }
        if (participants.stream().anyMatch(participant ->
                !isHost(session, participant)
                        && participant.getStatus() == ParticipantStatus.PENDING
                        && participant.getAmount() != null
                        && participant.getPayment() == null)) {
            return paymentStep(session);
        }
        if (allPayableMembersSettled(session, participants)
                && hostAmount(session, participants) > 0
                && hostStatus(session, participants) != ParticipantStatus.HOST_PAID) {
            return ProgressStep.FINAL_PAYMENT_REQUIRED.name();
        }
        if (participants.stream().anyMatch(participant ->
                !isHost(session, participant)
                        && (participant.getStatus() == ParticipantStatus.PAID || participant.getPayment() != null))) {
            return ProgressStep.PAYMENT_IN_PROGRESS.name();
        }

        return paymentStep(session);
    }

    // [be] 영은 260612 | 참여자 부담금이 모두 확정된 뒤, 대표자가 결제 요청하기를 눌렀는지로 대기/결제가능 단계를 구분한다.
    private static String paymentStep(DutchPaySessionEntity session) {
        if (session.getPayment_requested_at() != null) {
            return ProgressStep.PAYMENT_REQUESTED.name();
        }

        return session.getAmount_confirmed_at() != null
                ? ProgressStep.AMOUNT_CONFIRMED.name()
                : ProgressStep.AMOUNT_INPUT_COMPLETED.name();
    }

    private static boolean isHost(DutchPaySessionEntity session, DutchPayParticipantEntity participant) {
        return Objects.equals(session.getHost_user_id(), participant.getUser_id());
    }

    private static long hostAmount(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        if (participants == null) {
            return 0L;
        }

        return participants.stream()
                .filter(participant -> isHost(session, participant))
                .map(DutchPayParticipantEntity::getAmount)
                .filter(amount -> amount != null)
                .findFirst()
                .orElse(0L);
    }

    private static ParticipantStatus hostStatus(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        if (participants == null) {
            return null;
        }

        return participants.stream()
                .filter(participant -> isHost(session, participant))
                .map(DutchPayParticipantEntity::getStatus)
                .findFirst()
                .orElse(null);
    }

    private static boolean allPayableMembersSettled(
            DutchPaySessionEntity session,
            List<DutchPayParticipantEntity> participants) {
        if (participants == null) {
            return false;
        }

        List<DutchPayParticipantEntity> members = participants.stream()
                .filter(participant -> !isHost(session, participant))
                .filter(participant -> participant.getStatus() != ParticipantStatus.TIMEOUT)
                .filter(participant ->
                        participant.getStatus() != ParticipantStatus.REJECTED ||
                                isFailedPaymentParticipant(participant))
                .toList();

        return !members.isEmpty()
                && members.stream().allMatch(participant ->
                        participant.getStatus() == ParticipantStatus.PAID
                                || isFailedPaymentParticipant(participant));
    }

    private static boolean isFailedPaymentParticipant(DutchPayParticipantEntity participant) {
        return participant.getStatus() == ParticipantStatus.REJECTED
                && (participant.getPayment() != null || participant.getAmount() != null);
    }

    private enum ProgressStep {
        GROUP_CREATED,          // 그룹 생성 직후 또는 대표자만 있는 단계
        PARTICIPANT_CONFIRM,    // 참여자 초대/수락/인원 확정 대기 단계
        AMOUNT_INPUT,           // CUSTOM 금액 입력 대기 단계
        AMOUNT_INPUT_COMPLETED, // 모든 참여자 금액 입력 완료, 대표자 금액 확정 전 — 참여자는 수정 가능
        AMOUNT_CONFIRMED,       // 대표자 금액 확정, 결제 요청 전 — 참여자는 결제 버튼 없이 대기
        PAYMENT_REQUESTED,      // 대표자가 결제 요청 — 참여자 결제 진행 가능 단계
        PAYMENT_IN_PROGRESS,    // 참여자 결제 진행 중 단계
        FINAL_PAYMENT_REQUIRED, // 타임아웃 후 대표자 최종 결제 필요 단계
        COMPLETED,              // 더치페이 정상 완료 단계
        CANCELED,               // 대표자가 더치페이를 취소한 단계
        FAILED,                 // 더치페이 실패 단계
        TIMEOUT_HANDLED         // 타임아웃 처리 완료 단계
    }
}
