package com.erumpay.payment.dutch.domain.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 더치페이 링크 초대 생성 응답 DTO.
 *
 * <p>앱 친구가 아닌 사용자도 초대 링크로 세션에 참여할 수 있도록
 * 초대 토큰과 공유 URL을 내려준다.</p>
 */
@Builder
@Getter
public class DutchPayInviteLinkResponse {

    private Long session_id;
    private String invite_token;
    private String invite_url;
}
