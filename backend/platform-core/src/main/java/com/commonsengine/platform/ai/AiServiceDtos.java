package com.commonsengine.platform.ai;

import com.commonsengine.platform.geo.GeoPoint;

import java.util.List;

/**
 * DTOs for AI service integration: chat, moderation, and dispatch.
 *
 * <p>All types are immutable Java {@code record}s (JDK 21).
 */
public final class AiServiceDtos {

    private AiServiceDtos() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    // ------------------------------------------------------------------
    // Generic envelope
    // ------------------------------------------------------------------

    /**
     * Generic API response envelope.
     */
    public record ApiResponse<T>(
            boolean success,
            T data,
            AiErrorResponse error
    ) {
    }

    /**
     * Error detail returned by the AI service.
     */
    public record AiErrorResponse(
            String code,
            String message
    ) {
    }

    /**
     * Raised when the AI service call fails or returns an error.
     */
    public static final class AiServiceException extends RuntimeException {
        private final String code;

        public AiServiceException(String code, String message) {
            super(message);
            this.code = code;
        }

        public AiServiceException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    /**
     * Which AI provider to route to.
     */
    public enum AiService {
        CHAT,
        MODERATION,
        DISPATCH
    }

    public record ChatRequest(
            String sessionId,
            String userMessage
    ) {
    }

    public record ChatReply(
            String sessionId,
            String reply,
            String model
    ) {
    }

    // ------------------------------------------------------------------
    // Moderation
    // ------------------------------------------------------------------

    public enum ModerationCategory {
        HATE,
        HARASSMENT,
        VIOLENCE,
        SELF_HARM,
        SEXUAL,
        SPAM,
        NONE
    }

    public enum ModerationDecision {
        ALLOW,
        FLAG,
        BLOCK
    }

    public enum ContentSource {
        CONSUMER_PROFILE,
        WORKER_PROFILE,
        SERVICE_REQUEST,
        REVIEW,
        CHAT_MESSAGE,
        DISPUTE_DESCRIPTION
    }

    public record ModerationRequest(
            String content,
            ContentSource source
    ) {
    }

    public record ModerationResult(
            ModerationDecision decision,
            List<ModerationCategory> categories,
            double confidence
    ) {
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    public record WorkerLocation(
            String workerId,
            String name,
            GeoPoint location,
            double rating,
            int activeOrderCount
    ) {
    }

    public record DispatchRequest(
            String consumerId,
            GeoPoint pickup,
            GeoPoint dropoff,
            String serviceType,
            List<WorkerLocation> nearbyWorkers
    ) {
    }

    public record DispatchSuggestion(
            String workerId,
            double score,
            String reason
    ) {
    }

    public record DispatchResult(
            List<DispatchSuggestion> suggestions
    ) {
    }
}
