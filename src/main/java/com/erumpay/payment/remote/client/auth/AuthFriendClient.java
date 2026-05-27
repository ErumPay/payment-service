package com.erumpay.payment.remote.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateRequest;
import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateResponse;

@FeignClient(name = "authFriendClient", url = "${auth.base-url}")
public interface AuthFriendClient {

    @PostMapping("/internal/v1/friends/validate")
    AuthFriendValidateResponse validateFriend(@RequestBody AuthFriendValidateRequest request);
}
