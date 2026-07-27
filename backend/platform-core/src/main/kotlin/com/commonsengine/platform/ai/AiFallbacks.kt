package com.commonsengine.platform.ai

/**
 * AI 服务层降级策略 — 当 Python 服务不可用（熔断/超时/错误）时，
 * 核心业务层不应硬故障，而是退回到安全的本地兜底结果。
 *
 * 这体现架构文档「数据主权」与「反榨取」原则：
 * - 客服不可用 → 转人工（不阻塞用户）
 * - 审核不可用 → 标记待人工复审（宁可降效，不可放行违规）
 * - 调度不可用 → 返回空建议（由 Kotlin 侧 MatchingEngine 本地兜底，绝不饥饿派单）
 *
 * — Commons Engine Chief Engineer Bot（AI），#74
 */
object AiFallbacks {

    /** 客服降级：转人工。 */
    fun customerServiceFallback(userId: String?): ChatReply = ChatReply(
        reply = "智能客服暂时不可用，已为您转接人工客服，请稍候。",
        needsHuman = true,
        category = "fallback_human",
    )

    /** 内容审核降级：标记待人工复审（保守策略，不放行）。 */
    fun moderationFallback(): ModerationResult = ModerationResult(
        decision = ModerationDecision.FLAGGED,
        category = ModerationCategory.CLEAN,
        confidence = 0.0,
        reason = "AI 审核服务暂时不可用——保守标记待人工复审",
    )

    /** 调度降级：返回空建议（交由 Kotlin 侧本地匹配引擎兜底，不饥饿派单）。 */
    fun dispatchFallback(): DispatchResult = DispatchResult(
        suggestions = emptyList(),
        strategy = "fallback_empty",
    )
}
