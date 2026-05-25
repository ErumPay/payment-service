package com.erumpay.payment.dutch.domain.dto;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DutchPayInviteLinkResponse {

    private Long session_id;
    private String invite_token;
    private String invite_url;
}
