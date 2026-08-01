"""分类器注册表与工厂 — 按配置选择分类器实例，含 fallback 链。

使用方式：
    from common.nlp import get_classifier

    # 意图分类器（默认规则引擎）
    classifier = get_classifier("intent")
    result = classifier.classify("平台抽成多少？")

    # 或指定类型
    from common.nlp import ClassifierRegistry
    registry = ClassifierRegistry()
    classifier = registry.get("intent", backend="rule")

配置（环境变量）：
    COMMONS_INTENT_CLASSIFIER=rule  (默认) | huggingface | openai
    COMMONS_CONTENT_CLASSIFIER=rule (默认) | huggingface | openai

fallback 行为：
    如果指定的 NLP 后端不可用（依赖缺失 / 超时 / 异常），
    自动降级到 RuleBasedClassifier（source='fallback'）。
"""
from __future__ import annotations

import logging
import os

from common.nlp.base import (
    ClassificationResult,
    ContentClassifier,
    IntentClassifier,
)
from common.nlp.rule_based import (
    RuleBasedContentClassifier,
    RuleBasedIntentClassifier,
)

logger = logging.getLogger("ai-services.nlp")

# ── 适配器注册 ──────────────────────────────────────────
# 每种 backend 对应一个工厂函数。NLP 适配器（huggingface 等）在
# 依赖安装后动态注册。未安装时 import 失败会被 try/except 捕获，
# 自动降级到 rule。
_INTENT_FACTORIES: dict[str, type[IntentClassifier]] = {
    "rule": RuleBasedIntentClassifier,
}
_CONTENT_FACTORIES: dict[str, type[ContentClassifier]] = {
    "rule": RuleBasedContentClassifier,
}


def _try_register_huggingface() -> None:
    """尝试注册 HuggingFace 适配器——仅在 transformers 安装时可用。"""
    try:
        from common.nlp.huggingface_adapter import (  # type: ignore[import-not-found]
            HuggingFaceContentClassifier,
            HuggingFaceIntentClassifier,
        )

        _INTENT_FACTORIES["huggingface"] = HuggingFaceIntentClassifier
        _CONTENT_FACTORIES["huggingface"] = HuggingFaceContentClassifier
        logger.info("HuggingFace NLP 适配器已注册")
    except ImportError:
        logger.debug("HuggingFace transformers 未安装——规则引擎模式")


_try_register_huggingface()


class ClassifierRegistry:
    """分类器注册表——按 backend 名称获取分类器实例。

    自动 fallback：如果请求的 backend 不可用，降级到 rule。
    """

    def get_intent_classifier(
        self, backend: str | None = None
    ) -> IntentClassifier:
        backend = backend or os.environ.get("COMMONS_INTENT_CLASSIFIER", "rule")
        factory = _INTENT_FACTORIES.get(backend)
        if factory is None:
            logger.warning(
                "意图分类器 backend=%s 不可用，降级到 rule", backend
            )
            return RuleBasedIntentClassifier()
        return factory()

    def get_content_classifier(
        self, backend: str | None = None
    ) -> ContentClassifier:
        backend = backend or os.environ.get("COMMONS_CONTENT_CLASSIFIER", "rule")
        factory = _CONTENT_FACTORIES.get(backend)
        if factory is None:
            logger.warning(
                "内容审核分类器 backend=%s 不可用，降级到 rule", backend
            )
            return RuleBasedContentClassifier()
        return factory()

    # 便捷别名
    def get(
        self, kind: str, backend: str | None = None
    ) -> IntentClassifier | ContentClassifier:
        if kind == "intent":
            return self.get_intent_classifier(backend)
        elif kind == "content":
            return self.get_content_classifier(backend)
        raise ValueError(f"未知分类器类型: {kind}（支持: intent, content）")


_default_registry = ClassifierRegistry()


def get_classifier(
    kind: str, backend: str | None = None
) -> IntentClassifier | ContentClassifier:
    """获取默认注册表中的分类器实例。

    Args:
        kind: 'intent'（客服意图）或 'content'（内容审核）
        backend: 可选，指定后端（默认从环境变量读取，再默认 'rule'）
    """
    return _default_registry.get(kind, backend)
