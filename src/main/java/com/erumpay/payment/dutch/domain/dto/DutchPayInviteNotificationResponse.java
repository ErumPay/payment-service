package com.erumpay.payment.dutch.domain.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class DutchPayInviteNotificationResponse {

    private Long session_id;
    // [be] 영은 260610 | 알림 수신자가 링크를 눌렀을 때 acceptInviteLink로 입장할 수 있는 공유 토큰/URL이다.
    private String invite_token;
    private String invite_url;
    private List<Long> notified_user_ids;
}
