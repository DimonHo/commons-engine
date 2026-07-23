package com.commonsengine.platform.ai;

import com.commonsengine.platform.ai.AiServiceDtos.ChatReply;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchResult;
import com.commonsengine.platform.ai.AiServiceDtos.DispatchSuggestion;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationCategory;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationDecision;
import com.commonsengine.platform.ai.AiServiceDtos.ModerationResult;

import java.util.List;

/**
 * Static fallback values used when the AI service is unavailable or fails.
 */
public final class AiFallbacks {

    private AiFallbacks() {
        throw new AssertionError("Utility class — do not instantiate");
    }

    /**
     * Fallback chat reply used when the chat service is unavailable.
     */
    public static ChatReply chatFallback(String sessionId) {
        return new ChatReply(
                sessionId,
                "I'm sorry, I'm having trouble connecting to the service right now. Please try again shortly.",
                "fallback"
        );
    }

    /**
     * Fallback moderation result — conservative {@code FLAG} decision.
     */
    public static ModerationResult moderationFallback() {
        return new ModerationResult(
                ModerationDecision.FLAG,
                List.of(ModerationCategory.NONE),
                0.0
        );
    }

    /**
     * Fallback dispatch result — empty suggestions; callers should fall back
     * to the rule-based matching engine.
     */
    public static DispatchResult dispatchFallback() {
        return new DispatchResult(List.of());
    }

    /**
     * Build a simple fallback dispatch suggestion for a single worker.
     */
    public static DispatchSuggestion singleFallbackSuggestion(String workerId) {
        return new DispatchSuggestion(workerId, 0.5, "fallback — rule-based selection unavailable");
    }
}
