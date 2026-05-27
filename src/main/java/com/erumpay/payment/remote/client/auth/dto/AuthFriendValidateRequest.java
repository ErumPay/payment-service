package com.erumpay.payment.remote.client.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthFriendValidateRequest {

    private Long user_id;
    private Long friend_user_id;
}
