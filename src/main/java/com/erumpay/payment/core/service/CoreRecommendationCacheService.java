package com.erumpay.payment.core.service;

import java.time.Duration;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;
import com.erumpay.payment.core.exception.CustomException;
import com.erumpay.payment.core.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CoreRecommendationCacheService {

    private static final String RECOMMENDATION_CACHE_KEY_PREFIX = "payment:recommendation:";
    private static final Duration RECOMMENDATION_CACHE_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void save(Long paymentId, RecommendResponse recommendResponse) {
        if (paymentId == null || recommendResponse == null || recommendResponse.getResults() == null) {
            return;
        }

        String cacheKey = buildCacheKey(paymentId);
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(recommendResponse),
                    RECOMMENDATION_CACHE_TTL);
            log.info("recommendation cache saved. key={}, ttlSeconds={}",
                    cacheKey,
                    RECOMMENDATION_CACHE_TTL.toSeconds());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("recommendation cache save failed. paymentId={}", paymentId, e);
            throw new CustomException(ErrorCode.REC_CACHE_WRITE_FAILED, e);
        }
    }

    public RecommendResponse loadOrThrow(Long paymentId) {
        String cacheKey = buildCacheKey(paymentId);
        try {
            String cachedRecommendation = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedRecommendation == null || cachedRecommendation.isBlank()) {
                log.warn("recommendation cache missing. paymentId={}, key={}", paymentId, cacheKey);
                throw new CustomException(ErrorCode.RECOMMENDATION_SELECTION_INVALID);
            }

            return objectMapper.readValue(cachedRecommendation, RecommendResponse.class);
        } catch (CustomException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("recommendation cache read failed. paymentId={}, key={}", paymentId, cacheKey, e);
            throw new CustomException(ErrorCode.REC_CACHE_READ_FAILED, e);
        }
    }

    public Optional<RecommendResponse> find(Long paymentId) {
        if (paymentId == null) {
            return Optional.empty();
        }

        String cacheKey = buildCacheKey(paymentId);
        try {
            String cachedRecommendation = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedRecommendation == null || cachedRecommendation.isBlank()) {
                return Optional.empty();
            }

            RecommendResponse recommendResponse = objectMapper.readValue(cachedRecommendation, RecommendResponse.class);
            if (recommendResponse.getResults() == null) {
                return Optional.empty();
            }

            return Optional.of(recommendResponse);
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("recommendation cache replay read failed. paymentId={}, key={}", paymentId, cacheKey, e);
            return Optional.empty();
        }
    }

    private String buildCacheKey(Long paymentId) {
        return RECOMMENDATION_CACHE_KEY_PREFIX + paymentId;
    }
}
