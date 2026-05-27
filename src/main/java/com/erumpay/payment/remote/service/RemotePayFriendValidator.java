package com.erumpay.payment.remote.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.erumpay.payment.remote.client.auth.AuthFriendClient;
import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateRequest;
import com.erumpay.payment.remote.client.auth.dto.AuthFriendValidateResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RemotePayFriendValidator {

    private final AuthFriendClient authFriendClient;

    @Value("${app.remote-pay.friend-validation-enabled:false}")
    private boolean friendValidationEnabled;

    public void validate(Long requesterUserId, Long targetUserId) {
        if (requesterUserId == null || targetUserId == null || requesterUserId.equals(targetUserId)) {
            throw new CustomException(ErrorCode.BAD_REQUEST);
        }

        if (!friendValidationEnabled) {
            return;
        }

        AuthFriendValidateResponse response = authFriendClient.validateFriend(
                new AuthFriendValidateRequest(requesterUserId, targetUserId));
        if (response == null || !response.isFriend()) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
    }
}
