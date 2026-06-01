package com.erumpay.payment.dutch.domain.dto;

import java.util.List;
import java.util.Objects;

import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPayParticipantEntity.ParticipantStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.DutchPayStatus;
import com.erumpay.payment.dutch.domain.entity.DutchPaySessionEntity.SplitMethod;

import lombok.Builder;
import lombok.Getter;

/**
 * 더치페이 세션 상세 응답 DTO.
 *
 * <p>세션 상세 조회, 진행 중 세션 재진입, SSE 갱신 payload에서 공통으로 사용한다.
 * DB Entity를 그대로 노출하지 않고 프론트가 화면을 복원하는 데 필요한 세션 상태,
 * 참여자 상태, 금액, 진행 단계를 조립해서 내려준다.</p>
 */
@Builder
@Getter
public class DutchPaySessionDetailResponse {

    private Long session_id;
    private String dutch_order_no;
    private Long host_user_id;
    private Long merchant_id;
    private String order_name;
    private Long host_auth_payment_id;
    private Long total_amount;
    private Long remaining_amount;
    private String split_method;
    private String status;

    /**
     * 프론트 프로그레스바 복원용 세션 전체 진행 단계.
     *
     * <p>DB에 저장되는 값이 아니라 {@code session.status}, {@code split_method},
     * {@code participants.status}, {@code participants.amount}, {@code participants.payment_id}
     * 기준으로 응답 시점에 계산하는 파생 필드다.</p>
     */
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
                .order_name(session.getOrder_name())
                .host_auth_payment_id(session.getHost_auth_payment_id())
                .total_amount(session.getTotal_amount())
                .remaining_amount(session.getTotal_amount() - assignedAmount)
                .split_method(session.getSplit_method().name())
                .status(session.getStatus().name())
                .session_progress_step(resolveSessionProgressStep(session, participants))
                .participants(participants.stream()
                        .map(participant -> DutchPayParticipantResponse.fromEntity(participant, session.getHost_user_id()))
                        .toList())
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
        if (session.getStatus() == DutchPayStatus.TIMEOUT_HANDLED) {
            return hostAmount(session, participants) > 0
                    ? ProgressStep.FINAL_PAYMENT_REQUIRED.name()
                    : ProgressStep.TIMEOUT_HANDLED.name();
        }
        if (participants == null || participants.size() <= 1) {
            return ProgressStep.GROUP_CREATED.name();
        }
        if (participants.stream().anyMatch(participant -> participant.getStatus() == ParticipantStatus.INVITED)) {
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
            return ProgressStep.PAYMENT_REQUEST.name();
        }
        if (participants.stream().anyMatch(participant ->
                !isHost(session, participant)
                        && (participant.getStatus() == ParticipantStatus.PAID || participant.getPayment() != null))) {
            return ProgressStep.PAYMENT_IN_PROGRESS.name();
        }

        return ProgressStep.PAYMENT_REQUEST.name();
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

    private enum ProgressStep {
        GROUP_CREATED,          // 그룹 생성 직후 또는 대표자만 있는 단계
        PARTICIPANT_CONFIRM,    // 참여자 초대/수락/인원 확정 대기 단계
        AMOUNT_INPUT,           // CUSTOM 금액 입력 대기 단계
        PAYMENT_REQUEST,        // 참여자 결제 요청 가능 단계
        PAYMENT_IN_PROGRESS,    // 참여자 결제 진행 중 단계
        FINAL_PAYMENT_REQUIRED, // 타임아웃 후 대표자 최종 결제 필요 단계
        COMPLETED,              // 더치페이 정상 완료 단계
        FAILED,                 // 더치페이 실패 단계
        TIMEOUT_HANDLED         // 타임아웃 처리 완료 단계
    }
}
