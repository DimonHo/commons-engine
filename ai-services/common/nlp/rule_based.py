"""规则引擎适配器 — 将现有关键词路由包装为 IntentClassifier / ContentClassifier。

这是 #75 的第一步：在不改变现有行为的前提下，将规则引擎适配到统一模型层接口。
后续接入 NLP 模型时，规则引擎降级为 fallback。

设计说明：
- RuleBasedIntentClassifier 包装 customer_service/main.py 的 _classify 逻辑
- RuleBasedContentClassifier 包装 content_moderation/main.py 的 _moderate 逻辑
- 两者 source 字段标注为 'rule'，便于审计追踪
"""
from __future__ import annotations

import re

from common.nlp.base import (
    ClassificationResult,
    ContentClassifier,
    IntentClassifier,
    ModerationClassification,
)

# ── 客服意图规则（从 customer_service/main.py 提取，保持一致） ──
_INTENT_RULES: list[tuple[list[str], str]] = [
    (["抽成", "佣金", "手续费", "平台费"], "category_commission"),
    (["评价", "评分", "信用", "星级"], "category_rating"),
    (["退款", "退钱", "投诉"], "category_refund"),
    (["人工", "客服", "转人工", "真人"], "category_human"),
]

# ── 内容审核规则（从 content_moderation/main.py 提取，保持一致） ──
_MODERATION_RULES: list[tuple[list[str], ModerationClassification]] = [
    (["辱骂占位"], ModerationCategory_ABUSE := ModerationClassification.ABUSE),
    (["加微信", "加v信", "微信号", "扫码进群", "私聊"], ModerationClassification.SPAM),
]

_PHONE_RE = re.compile(r"1[3-9]\d{9}")
_ID_CARD_RE = re.compile(r"\d{17}[\dXx]")


class RuleBasedIntentClassifier(IntentClassifier):
    """基于关键词路由的意图分类器（规则引擎适配器）。

    source = 'rule'，置信度固定（规则命中=0.85，未命中=0.0）。
    """

    def classify(self, message: str) -> ClassificationResult:
        text = message.lower()
        for keywords, category in _INTENT_RULES:
            if any(kw in text for kw in keywords):
                is_human = category == "category_human"
                reason = f"命中关键词规则: {category}" + (
                    "（转人工）" if is_human else ""
                )
                return ClassificationResult(
                    category=category,
                    confidence=0.85,
                    reason=reason,
                    source="rule",
                )
        return ClassificationResult(
            category=None,
            confidence=0.0,
            reason="未命中任何关键词规则——转人工兜底",
            source="rule",
        )


class RuleBasedContentClassifier(ContentClassifier):
    """基于规则词典的内容审核分类器（规则引擎适配器）。

    source = 'rule'，PII 检测置信度 0.95，关键词命中 0.8，默认通过 0.7。
    """

    def classify(self, content: str) -> ClassificationResult:
        text = content.lower()

        # 1. PII 检测
        if _PHONE_RE.search(content) or _ID_CARD_RE.search(content):
            return ClassificationResult(
                category=ModerationClassification.PII.value,
                confidence=0.95,
                reason="检测到疑似手机号或身份证号",
                source="rule",
            )

        # 2. 关键词规则
        for keywords, category in _MODERATION_RULES:
            if any(kw in text for kw in keywords):
                return ClassificationResult(
                    category=category.value,
                    confidence=0.8,
                    reason=f"命中规则词典: {category.value}",
                    source="rule",
                )

        # 3. 默认通过
        return ClassificationResult(
            category=ModerationClassification.CLEAN.value,
            confidence=0.7,
            reason="未命中任何违规规则",
            source="rule",
        )
