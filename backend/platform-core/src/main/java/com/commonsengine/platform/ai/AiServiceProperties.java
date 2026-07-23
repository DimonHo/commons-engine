package com.commonsengine.platform.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for the AI services integration.
 *
 * <p>Bound from the {@code ai-services.*} prefix in application properties.
 */
@ConfigurationProperties(prefix = "ai-services")
public class AiServiceProperties {

    /** Base URL of the AI service backend. */
    private String baseUrl = "http://localhost:8000";

    /** API key for authentication. */
    private String apiKey = "";

    /** HTTP request timeout. */
    private Duration timeout = Duration.ofSeconds(10);

    /** Circuit-breaker config. */
    private CircuitBreaker circuitBreaker = new CircuitBreaker();

    /** Retry config. */
    private Retry retry = new Retry();

    // ---- nested config ------------------------------------------------

    public static class CircuitBreaker {
        private double failureRateThreshold = 50.0;
        private int slidingWindowSize = 10;
        private int minimumNumberOfCalls = 5;
        private Duration waitDurationInOpenState = Duration.ofSeconds(30);

        public double getFailureRateThreshold() { return failureRateThreshold; }
        public void setFailureRateThreshold(double v) { this.failureRateThreshold = v; }

        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int v) { this.slidingWindowSize = v; }

        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int v) { this.minimumNumberOfCalls = v; }

        public Duration getWaitDurationInOpenState() { return waitDurationInOpenState; }
        public void setWaitDurationInOpenState(Duration v) { this.waitDurationInOpenState = v; }
    }

    public static class Retry {
        private int maxAttempts = 3;
        private Duration waitDuration = Duration.ofMillis(500);

        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }

        public Duration getWaitDuration() { return waitDuration; }
        public void setWaitDuration(Duration v) { this.waitDuration = v; }
    }

    // ---- getters / setters --------------------------------------------

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration timeout) { this.timeout = timeout; }

    public CircuitBreaker getCircuitBreaker() { return circuitBreaker; }
    public void setCircuitBreaker(CircuitBreaker circuitBreaker) { this.circuitBreaker = circuitBreaker; }

    public Retry getRetry() { return retry; }
    public void setRetry(Retry retry) { this.retry = retry; }
}
