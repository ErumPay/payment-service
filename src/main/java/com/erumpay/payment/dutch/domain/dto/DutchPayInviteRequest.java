package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 더치페이 앱 친구 초대 요청 DTO.
 *
 * <p>대표자가 세션에 추가할 사용자 ID 목록을 전달하면, 서버가 참여자 row를 생성하고
 * 이후 알림 이벤트를 발행할 수 있다.</p>
 */
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayInviteRequest {

    @NotEmpty
    private List<@NotNull @Positive Long> user_ids;
}
