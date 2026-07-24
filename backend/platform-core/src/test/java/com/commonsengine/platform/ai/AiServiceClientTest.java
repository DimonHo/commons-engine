package com.commonsengine.platform.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.commonsengine.platform.ai.AiServiceDtos.ChatReply;
import com.commonsengine.platform.ai.AiServiceDtos.ChatRequest;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchRequest;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchResult;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationRequest;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationResult;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * AiServiceClient contract + fallback tests.
 *
 * <p>Converted from the Kotlin original. The Java {@code AiServiceClient} uses
 * Spring's {@link org.springframework.web.client.RestClient RestClient} pointed
 * at a base URL configured via {@link AiServiceProperties}. When the backing AI
 * service is unreachable, every public method must degrade to a safe fallback
 * rather than throwing.
 *
 * <p>These tests point the client at a closed port (connection refused) to
 * exercise the fallback paths deterministically without an HTTP mock server
 * (mockwebserver is not a dependency of platform-core).
 */
class AiServiceClientTest {

    private AiServiceClient client;

    @BeforeEach
    void setUp() {
        AiServiceProperties props = new AiServiceProperties();
        // Point at a port nothing listens on → immediate connection failure → fallback.
        props.setBaseUrl("http://127.0.0.1:9");
        props.setApiKey("test-key");

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .slidingWindowSize(4)
                .minimumNumberOfCalls(2)
                .waitDurationInOpenState(Duration.ofSeconds(60))
                .build();
        CircuitBreaker circuitBreaker = CircuitBreaker.of("aiService", cbConfig);

        RetryConfig retryConfig = RetryConfig.custom()
                .maxAttempts(1)
                .waitDuration(Duration.ofMillis(10))
                .build();
        Retry retry = Retry.of("aiService", retryConfig);

        client = new AiServiceClient(props, circuitBreaker, retry);
    }

    // ── customer-service ──────────────────────────────────

    @Nested
    @DisplayName("客服对话 chat()")
    class CustomerService {

        @Test
        void fallbackWhenServiceUnreachable() {
            ChatReply result = client.chat(new ChatRequest("s-1", "抽成多少"));

            assertNotNull(result, "chat() must never return null");
            assertNotNull(result.reply(), "fallback reply must be present");
            assertEquals("fallback", result.model(), "fallback model identifier expected");
            assertFalse(result.reply().isBlank());
        }
    }

    // ── content-moderation ────────────────────────────────

    @Nested
    @DisplayName("内容审核 moderate()")
    class ContentModeration {

        @Test
        void fallbackConservativelyFlagsWhenServiceUnreachable() {
            ModerationResult result = client.moderate(
                    new ModerationRequest("任意内容", AiServiceDtos.ContentSource.REVIEW));

            assertNotNull(result, "moderate() must never return null");
            assertEquals(AiServiceDtos.ModerationDecision.FLAG, result.decision(),
                    "审核不可用时应保守标记");
            assertEquals(0.0, result.confidence(), 0.001);
            assertNotNull(result.categories());
        }
    }

    // ── dispatch-optimizer ────────────────────────────────

    @Nested
    @DisplayName("调度建议 dispatch()")
    class DispatchOptimizer {

        @Test
        void fallbackReturnsEmptySuggestionsWhenServiceUnreachable() {
            DispatchResult result = client.dispatch(
                    new DispatchRequest("c-1", null, null, "RIDE_HAILING", java.util.List.of()));

            assertNotNull(result, "dispatch() must never return null");
            assertTrue(result.suggestions().isEmpty(),
                    "调度不可用时应返回空建议，绝不饥饿派单");
        }
    }

    // ── 熔断器 ────────────────────────────────────────────

    @Nested
    @DisplayName("熔断器与连续故障")
    class CircuitBreakerTests {

        @Test
        void repeatedFailuresOpenCircuitAndSubsequentCallsStillDegradeGracefully() {
            // 连续失败调用——每次都应走降级，绝不向调用方抛异常
            ChatReply r1 = client.chat(new ChatRequest("s-1", "1"));
            ChatReply r2 = client.chat(new ChatRequest("s-2", "2"));

            assertNotNull(r1);
            assertNotNull(r2);

            // 熔断器打开后第三次仍应直接降级
            ChatReply r3 = client.chat(new ChatRequest("s-3", "3"));
            assertNotNull(r3, "熔断打开后应直接降级");
            assertEquals("fallback", r3.model());
        }
    }
}
