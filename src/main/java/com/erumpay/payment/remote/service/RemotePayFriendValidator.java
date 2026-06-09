package com.erumpay.payment.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.client.auth.AuthFriendClient;
import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateResponse;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RemotePayFriendValidator {

    private final AuthFriendClient authFriendClient;

    @Value("${app.remote-pay.friend-validation-enabled:true}")
    private boolean friendValidationEnabled;

    public void validate(Long requesterUserId, Long targetUserId) {
        if (requesterUserId == null) {
            throw new CustomException(ErrorCode.RMT_INVALID_REQUEST);
        }
        if (targetUserId == null) {
            throw new CustomException(ErrorCode.RMT_TARGET_REQUIRED);
        }
        if (requesterUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.RMT_REQUESTER_TARGET_SAME);
        }

        if (!friendValidationEnabled) {
            return;
        }

        AuthFriendValidateResponse response;
        try {
            response = authFriendClient.validateFriend(requesterUserId, targetUserId);
        } catch (FeignException e) {
            log.warn("Auth friend validation failed. status={}, requesterUserId={}, targetUserId={}",
                    e.status(),
                    requesterUserId,
                    targetUserId);
            throw new CustomException(ErrorCode.AUTH_FRIEND_VALIDATE_FAILED, e);
        }
        if (response == null || !response.isFriend()) {
            throw new CustomException(ErrorCode.RMT_FRIEND_NOT_ALLOWED);
        }
    }
}
