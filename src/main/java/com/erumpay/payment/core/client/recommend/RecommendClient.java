package com.erumpay.payment.core.client.recommend;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import com.erumpay.payment.core.client.recommend.dto.RecommendRequest;
import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;

import io.swagger.v3.oas.annotations.parameters.RequestBody;

@FeignClient(name = "recommendClient", url = "${recommend.base-url}")
public interface RecommendClient {

    @PostMapping(value = "/internal/v1/recommendations/calculate", consumes = "application/json")
    RecommendResponse recommentListRequest(
            @RequestBody RecommendRequest request);

}
