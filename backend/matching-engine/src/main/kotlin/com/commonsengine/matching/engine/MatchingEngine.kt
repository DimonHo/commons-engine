package com.commonsengine.matching.engine

import com.commonsengine.matching.strategy.AntiExploitationConfig
import com.commonsengine.matching.strategy.FairRoundRobinStrategy
import com.commonsengine.matching.strategy.MatchingStrategy
import com.commonsengine.matching.strategy.NearestFirstStrategy
import com.commonsengine.platform.domain.MatchResult
import com.commonsengine.platform.domain.ServiceRequest
import com.commonsengine.platform.domain.Worker
import org.springframework.stereotype.Service

/**
 * 匹配引擎核心。
 *
 * 支持运行时切换策略——合作社按区域配置匹配规则。
 * 默认使用距离优先策略。
 */
@Service
class MatchingEngine(
    private val strategies: Map<String, MatchingStrategy> = mapOf(
        "nearest-first" to NearestFirstStrategy(),
        "fair-round-robin" to FairRoundRobinStrategy(),
    ),
) {
    @Volatile
    private var activeStrategy: String = "nearest-first"

    /** 配置当前使用的匹配策略 */
    fun setStrategy(strategyName: String) {
        require(strategies.containsKey(strategyName)) {
            "未知策略: $strategyName，可用: ${strategies.keys}"
        }
        activeStrategy = strategyName
    }

    /** 获取当前策略名称 */
    fun currentStrategy(): String = activeStrategy

    /** 可用的策略列表 */
    fun availableStrategies(): Set<String> = strategies.keys

    /**
     * 执行匹配
     *
     * @param request 服务请求
     * @param candidates 候选劳动者列表
     * @return 匹配结果（含可解释理由），无匹配则 null
     */
    fun match(request: ServiceRequest, candidates: List<Worker>): MatchResult? {
        val strategy = strategies[activeStrategy]
            ?: throw IllegalStateException("策略 '$activeStrategy' 未注册")
        return strategy.match(request, candidates)
    }
}
