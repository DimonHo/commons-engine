package com.commonsengine.matching.strategy

import com.commonsengine.platform.domain.MatchResult
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.Worker

/**
 * 匹配策略接口——算法可配置，公地引擎不硬编码。
 *
 * 反榨取约束：所有策略实现必须遵守 [MatchingStrategy.AntiExploitationConfig]。
 */
interface MatchingStrategy {

    /** 策略名称（用于可解释性日志） */
    val name: String

    /**
     * 从候选劳动者中选出最优匹配
     *
     * @return 匹配结果（含可解释理由），如果无合格候选则返回 null
     */
    fun match(request: ServiceRequest, candidates: List<Worker>): MatchResult?
}

/**
 * 反榨取配置——防止设计出系统性压低工资的派单黑箱。
 *
 * 这些参数应由合作社全体大会制定，不可由引擎开发者单方面设定。
 * 最终值标注"需人类维护者/全体大会决定"。
 */
data class AntiExploitationConfig(
    /** 最大匹配半径（米）——防止强制派远单 */
    val maxMatchRadiusMeters: Double = 5_000.0,
    /** 劳动者同时最大活跃订单数——防止疲劳过载 */
    val maxActiveOrders: Int = 3,
    /** 新人保护期——注册 X 天内的劳动者享受优先匹配 */
    val newcomerProtectionDays: Int = 7,  // TODO: 需配合 Worker 注册时间字段
)
