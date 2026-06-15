package com.erumpay.payment.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.erumpay.payment.core.client.recommend.dto.RecommendResponse;
import com.erumpay.payment.core.domain.dto.response.CoreSseEventResponse;
import com.erumpay.payment.core.domain.dto.sse.CoreSseEventType;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CoreSseServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private CoreSseTopicProperties coreSseTopicProperties;

    @Mock
    private CoreRecommendationCacheService coreRecommendationCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolveRecommendationEventForReplayFallsBackToRedisCache() {
        CoreSseService coreSseService = new CoreSseService(
                stringRedisTemplate,
                objectMapper,
                coreSseTopicProperties,
                coreRecommendationCacheService);
        RecommendResponse recommendResponse = RecommendResponse.builder()
                .paymentId(101L)
                .results(List.of(
                        RecommendResponse.Result.builder()
                                .strategyType("BENEFIT_SINGLE")
                                .reason("redis fallback")
                                .cards(List.of())
                                .build()))
                .build();
        when(coreRecommendationCacheService.find(101L)).thenReturn(Optional.of(recommendResponse));

        CoreSseEventResponse event = coreSseService.resolveRecommendationEventForReplay(101L);

        assertThat(event).isNotNull();
        assertThat(event.getEventType()).isEqualTo(CoreSseEventType.RECOMMENDATION_SUCCEEDED);
        assertThat(event.getPaymentId()).isEqualTo(101L);
        assertThat(event.getPayload()).isInstanceOf(RecommendResponse.class);
        RecommendResponse payload = (RecommendResponse) event.getPayload();
        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getReason()).isEqualTo("redis fallback");
    }

    @Test
    void resolveRecommendationEventForReplayUsesLocalCacheBeforeRedisLookup() {
        CoreSseService coreSseService = new CoreSseService(
                stringRedisTemplate,
                objectMapper,
                coreSseTopicProperties,
                coreRecommendationCacheService);
        CoreSseEventResponse cachedEvent = CoreSseEventResponse.of(
                CoreSseEventType.RECOMMENDATION_SUCCEEDED,
                202L,
                Map.of("source", "local"));
        when(coreRecommendationCacheService.find(202L)).thenReturn(Optional.empty());
        coreSseService.applyPaymentUpdatedFromRedis(202L, cachedEvent);

        CoreSseEventResponse replayEvent = coreSseService.resolveRecommendationEventForReplay(202L);

        assertThat(replayEvent).isSameAs(cachedEvent);
        verify(coreRecommendationCacheService, times(1)).find(202L);
    }

    @Test
    void resolveEventForDispatchReloadsRecommendationPayloadFromRedisCache() {
        CoreSseService coreSseService = new CoreSseService(
                stringRedisTemplate,
                objectMapper,
                coreSseTopicProperties,
                coreRecommendationCacheService);
        RecommendResponse recommendResponse = RecommendResponse.builder()
                .paymentId(303L)
                .results(List.of(
                        RecommendResponse.Result.builder()
                                .strategyType("BENEFIT_SPLIT")
                                .reason("shared cache")
                                .cards(List.of())
                                .build()))
                .build();
        CoreSseEventResponse redisEvent = CoreSseEventResponse.builder()
                .eventType(CoreSseEventType.RECOMMENDATION_SUCCEEDED)
                .paymentId(303L)
                .payload(List.of())
                .occurredAt(Instant.parse("2026-06-15T00:00:00Z"))
                .build();
        when(coreRecommendationCacheService.find(303L)).thenReturn(Optional.of(recommendResponse));

        CoreSseEventResponse resolvedEvent = coreSseService.resolveEventForDispatch(303L, redisEvent);

        assertThat(resolvedEvent.getEventType()).isEqualTo(CoreSseEventType.RECOMMENDATION_SUCCEEDED);
        assertThat(resolvedEvent.getOccurredAt()).isEqualTo(redisEvent.getOccurredAt());
        assertThat(resolvedEvent.getPayload()).isInstanceOf(RecommendResponse.class);
        RecommendResponse payload = (RecommendResponse) resolvedEvent.getPayload();
        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getReason()).isEqualTo("shared cache");
    }
}
