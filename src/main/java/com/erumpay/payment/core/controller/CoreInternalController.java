package com.erumpay.payment.core.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erumpay.payment.core.domain.dto.PaymentAllFetchRequest;
import com.erumpay.payment.core.domain.dto.PaymentAllFetchResponse;
import com.erumpay.payment.core.domain.dto.UserWithdrawalResponse;
import com.erumpay.payment.core.service.CoreService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Core Internal Payment", description = "카드/추천 서비스 연동용 Core 내부 API")
public class CoreInternalController {

    private final CoreService coreService;

    @PostMapping("/users/{userId}/recommendation-usage-summary")
    @Operation(summary = "추천용 결제 사용 집계 조회", description = "사용자의 기간별 결제 금액, 가맹점별 사용량, 카드별 사용량을 조회한다.")
    public ResponseEntity<PaymentAllFetchResponse> getRecommendationUsageSummary(
            @Parameter(description = "조회 대상 사용자 ID", required = true) @PathVariable("userId") Long userId,
            @Valid @RequestBody PaymentAllFetchRequest request) {
        return ResponseEntity.ok(coreService.getRecommendationUsageSummary(userId, request));
    }

    // [be] 다윤 260605 20:00 | 사용자 회원 탈퇴 시 미결제건 조회
    @GetMapping("/users/{userId}/withdrawal-validation")
    @Operation(summary = "회원 탈퇴 가능 여부 조회", description = "미결제 또는 처리 중인 결제 건이 있으면 탈퇴 불가로 응답한다.")
    public ResponseEntity<UserWithdrawalResponse> getWithdrawalValidate(
            @Parameter(description = "조회 대상 사용자 ID", required = true) @PathVariable("userId") Long userId) {

        return ResponseEntity.ok(coreService.getWithdrawalValidate(userId));
    }
}
