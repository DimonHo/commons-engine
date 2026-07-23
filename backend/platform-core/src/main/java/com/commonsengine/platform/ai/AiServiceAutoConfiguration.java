package com.commonsengine.platform.ai;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Wires up the AI-service {@link CircuitBreaker} and {@link Retry} beans from
 * {@link AiServiceProperties}.
 */
@Configuration
@EnableConfigurationProperties(AiServiceProperties.class)
public class AiServiceAutoConfiguration {

    @Bean("aiServiceCircuitBreaker")
    public CircuitBreaker aiServiceCircuitBreaker(AiServiceProperties properties) {
        AiServiceProperties.CircuitBreaker cb = properties.getCircuitBreaker();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold((float) cb.getFailureRateThreshold())
                .slidingWindowSize(cb.getSlidingWindowSize())
                .minimumNumberOfCalls(cb.getMinimumNumberOfCalls())
                .waitDurationInOpenState(cb.getWaitDurationInOpenState())
                .build();
        return CircuitBreaker.of("aiService", config);
    }

    @Bean("aiServiceRetry")
    public Retry aiServiceRetry(AiServiceProperties properties) {
        AiServiceProperties.Retry rt = properties.getRetry();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(rt.getMaxAttempts())
                .waitDuration(rt.getWaitDuration())
                .build();
        return Retry.of("aiService", config);
    }
}
