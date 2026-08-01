"""模型层抽象 — NLP 分类器统一接口（ARCHITECTURE.md 3.8）。

设计原则：
1. **不锁定供应商**：定义统一的 IntentClassifier / ContentClassifier 协议，
   供应商适配器实现协议，通过配置切换。
2. **规则引擎作为 fallback + 可解释性兜底**：NLP 模型不可用时自动回退。
3. **数据主权优先**：默认使用本地部署的开源模型路径，云端 API 通过适配器接入。

#75 实现：
- `IntentClassifier`：客服意图分类（替代 customer_service 关键词路由）
- `ContentClassifier`：内容审核分类（替代 content_moderation 规则匹配）
- `RuleBasedIntentClassifier` / `RuleBasedContentClassifier`：现有规则引擎的适配器
- `ClassifierRegistry`：按配置选择分类器实例，含 fallback 链
"""
from common.nlp.base import (
    ClassificationResult,
    ContentClassifier,
    IntentClassifier,
    ModerationClassification,
)
from common.nlp.registry import ClassifierRegistry, get_classifier

__all__ = [
    "ClassificationResult",
    "ContentClassifier",
    "IntentClassifier",
    "ModerationClassification",
    "ClassifierRegistry",
    "get_classifier",
]
