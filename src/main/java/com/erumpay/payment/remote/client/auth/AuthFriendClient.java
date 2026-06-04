package com.erumpay.payment.remote.client.auth;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateResponse;

@FeignClient(name = "authFriendClient", url = "${auth.base-url}")
public interface AuthFriendClient {

    @GetMapping("/internal/v1/friends/check")
    AuthFriendValidateResponse validateFriend(
            @RequestParam("userId") Long userId,
            @RequestParam("friendUserId") Long friendUserId);
}
