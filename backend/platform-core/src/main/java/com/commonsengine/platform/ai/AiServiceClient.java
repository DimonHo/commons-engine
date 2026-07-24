package com.commonsengine.platform.ai;

import com.commonsengine.platform.ai.AiServiceDtos.AiServiceException;
import com.commonsengine.platform.ai.AiServiceDtos.ChatReply;
import com.commonsengine.platform.ai.AiServiceDtos.ChatRequest;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchResult;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchRequest;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationResult;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * HTTP client that calls the AI services backend, wrapped with
 * Resilience4j {@link CircuitBreaker} and {@link Retry}.
 *
 * <p>All public methods fall back to safe defaults when the AI service is
 * unavailable.
 */
@Component
public class AiServiceClient {

    private static final Logger log = LoggerFactory.getLogger(AiServiceClient.class);

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public AiServiceClient(AiServiceProperties properties,
                           CircuitBreaker circuitBreaker,
                           Retry retry) {
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("X-API-Key", properties.getApiKey())
                .build();
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    public ChatReply chat(ChatRequest request) {
        try {
            return executeWithResilience(() -> post("/api/chat", request, ChatReply.class));
        } catch (Exception e) {
            log.warn("Chat service failed, using fallback: {}", e.getMessage());
            return AiFallbacks.chatFallback(request.sessionId());
        }
    }

    // ------------------------------------------------------------------
    // Moderation
    // ------------------------------------------------------------------

    public ModerationResult moderate(ModerationRequest request) {
        try {
            return executeWithResilience(() -> post("/api/moderate", request, ModerationResult.class));
        } catch (Exception e) {
            log.warn("Moderation service failed, using fallback: {}", e.getMessage());
            return AiFallbacks.moderationFallback();
        }
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    public DispatchResult dispatch(DispatchRequest request) {
        try {
            return executeWithResilience(() -> post("/api/dispatch", request, DispatchResult.class));
        } catch (Exception e) {
            log.warn("Dispatch service failed, using fallback: {}", e.getMessage());
            return AiFallbacks.dispatchFallback();
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * Functional interface for a checked HTTP call.
     */
    @FunctionalInterface
    private interface HttpCall<T> {
        T execute() throws Exception;
    }

    private <T> T executeWithResilience(HttpCall<T> call) throws Exception {
        return CircuitBreaker.decorateCallable(circuitBreaker,
                Retry.decorateCallable(retry, () -> {
                    try {
                        return call.execute();
                    } catch (RuntimeException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })).call();
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        return restClient.post()
                .uri(path)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, resp) -> {
                    throw new AiServiceException(
                            "AI_HTTP_" + resp.getStatusCode().value(),
                            "AI service returned HTTP " + resp.getStatusCode().value());
                })
                .body(responseType);
    }

    // Expose for tests
    boolean isCircuitBreakerOpen() {
        return circuitBreaker.getState() == CircuitBreaker.State.OPEN;
    }
}
