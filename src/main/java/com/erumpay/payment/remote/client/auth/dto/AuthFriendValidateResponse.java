package com.erumpay.payment.remote.client.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthFriendValidateResponse {

    private Boolean friend;

    public boolean isFriend() {
        return Boolean.TRUE.equals(friend);
    }
}
