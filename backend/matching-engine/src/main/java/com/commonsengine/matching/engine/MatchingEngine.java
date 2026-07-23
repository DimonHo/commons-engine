package com.commonsengine.matching.engine;

import com.commonsengine.matching.strategy.AntiExploitationConfig;
import com.commonsengine.matching.strategy.FairRoundRobinStrategy;
import com.commonsengine.matching.strategy.MatchingStrategy;
import com.commonsengine.matching.strategy.NearestFirstStrategy;
import com.commonsengine.platform.domain.Model.MatchResult;
import com.commonsengine.platform.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 匹配引擎（#62）
 *
 * 策略注册中心 + 匹配调度入口。
 *
 * 职责：
 * 1. 注册并管理可用匹配策略
 * 2. 切换当前活跃策略
 * 3. 执行匹配（委托给当前策略）
 *
 * 策略选择体现「反榨取」原则——默认使用公平轮转，
 * 可按场景切换为最近优先。
 */
@Service
public class MatchingEngine {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    /** 默认策略名 */
    private static final String DEFAULT_STRATEGY = "fair-round-robin";

    private final Map<String, MatchingStrategy> strategies = new LinkedHashMap<>();
    private String currentStrategyName;

    public MatchingEngine() {
        register(new NearestFirstStrategy());
        register(new FairRoundRobinStrategy());
        this.currentStrategyName = DEFAULT_STRATEGY;
    }

    @PostConstruct
    void init() {
        log.info("匹配引擎初始化完成，可用策略: {}，当前策略: {}",
                strategies.keySet(), currentStrategyName);
    }

    /** 注册策略 */
    public void register(MatchingStrategy strategy) {
        strategies.put(strategy.name(), strategy);
    }

    /** 切换当前策略 */
    public void useStrategy(String name) {
        if (!strategies.containsKey(name)) {
            throw new BusinessRuleException(
                    "UNKNOWN_STRATEGY",
                    "未知匹配策略: " + name + "。可用: " + strategies.keySet()
            );
        }
        this.currentStrategyName = name;
        log.info("匹配策略已切换: {}", name);
    }

    /** 当前策略名 */
    public String currentStrategy() {
        return currentStrategyName;
    }

    /** 所有可用策略名 */
    public Set<String> availableStrategies() {
        return strategies.keySet();
    }

    /**
     * 执行匹配——委托给当前策略。
     *
     * @param request   服务请求
     * @param candidates 候选劳动者
     * @return 匹配结果；无合适候选返回 null
     */
    public MatchResult match(com.commonsengine.platform.domain.Model.ServiceRequest request,
                              List<com.commonsengine.platform.domain.Model.Worker> candidates) {
        MatchingStrategy strategy = strategies.get(currentStrategyName);
        MatchResult result = strategy.match(request, candidates);
        if (result == null) {
            log.info("无匹配候选 request={} strategy={} candidateCount={}",
                    request, currentStrategyName, candidates != null ? candidates.size() : 0);
        } else {
            log.info("匹配成功 strategy={} score={} workerId={}",
                    currentStrategyName, result.score(), result.workerId());
        }
        return result;
    }

    /** 获取策略实例（测试用） */
    MatchingStrategy getStrategy(String name) {
        return strategies.get(name);
    }
}
