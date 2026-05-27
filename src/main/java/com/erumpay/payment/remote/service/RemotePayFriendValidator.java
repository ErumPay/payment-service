package com.erumpay.payment.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.client.auth.AuthFriendClient;
import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateRequest;
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
        if (requesterUserId == null || targetUserId == null || requesterUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        if (!friendValidationEnabled) {
            return;
        }

        AuthFriendValidateResponse response;
        try {
            response = authFriendClient.validateFriend(
                    new AuthFriendValidateRequest(requesterUserId, targetUserId));
        } catch (FeignException e) {
            log.warn("Auth friend validation failed. status={}, requesterUserId={}, targetUserId={}",
                    e.status(),
                    requesterUserId,
                    targetUserId);
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
        if (response == null || !response.isFriend()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
