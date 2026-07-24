package com.commonsengine.matching.strategy;

import com.commonsengine.platform.domain.Model.MatchResult;

/**
 * 匹配策略接口（策略模式）。
 *
 * 每个策略封装一种「劳动者 ↔ 服务请求」的匹配算法。
 * 策略选择体现「反榨取」原则——不同场景用不同公平性规则。
 *
 * Kotlin sealed class / interface → sealed interface + permits。
 */
public sealed interface MatchingStrategy
        permits NearestFirstStrategy, FairRoundRobinStrategy {

    /**
     * 在候选劳动者中选出最佳匹配。
     *
     * @param request   服务请求
     * @param candidates 候选劳动者列表
     * @return 匹配结果（含距离、策略名、可解释原因），无合适候选时返回 null
     */
    MatchResult match(com.commonsengine.platform.domain.Model.ServiceRequest request,
                      java.util.List<com.commonsengine.platform.domain.Model.Worker> candidates);

    /** 策略名称——用于日志、API 响应与策略切换。 */
    String name();
}
