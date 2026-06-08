package com.erumpay.payment.remote.client.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AuthFriendValidateResponse {

    @JsonProperty("isFriend")
    private Boolean friend;

    public boolean isFriend() {
        return Boolean.TRUE.equals(friend);
    }
}
