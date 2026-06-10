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
    private String invite_token;
    private String invite_url;
    private List<Long> notified_user_ids;
}
