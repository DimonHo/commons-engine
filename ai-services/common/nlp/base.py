"""NLP 分类器抽象基类与协议。

定义两个核心协议：
- IntentClassifier：客服意图分类（commission / rating / refund / human / unknown）
- ContentClassifier：内容审核分类（politics / abuse / spam / pii / clean）

所有供应商适配器实现这些协议。ClassificationResult 包含分类结果 +
置信度 + 可解释性依据（满足架构文档「算法透明」原则）。
"""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum


class ClassificationResult:
    """通用分类结果。

    Attributes:
        category: 分类标签（具体值由子类定义）
        confidence: 置信度 0.0-1.0
        reason: 可解释性依据——为何做出此分类
        source: 分类来源（'rule' / 'model' / 'fallback'），用于审计追踪
    """

    def __init__(
        self,
        category: str | None,
        confidence: float,
        reason: str,
        source: str = "model",
    ) -> None:
        self.category = category
        self.confidence = max(0.0, min(1.0, confidence))
        self.reason = reason
        self.source = source

    def __repr__(self) -> str:
        return (
            f"ClassificationResult(category={self.category!r}, "
            f"confidence={self.confidence:.2f}, source={self.source!r})"
        )


class IntentClassifier(ABC):
    """客服意图分类器协议。

    输入用户消息文本，输出意图分类结果。
    实现方需覆盖 classify() 方法。
    """

    @abstractmethod
    def classify(self, message: str) -> ClassificationResult:
        """对用户消息进行意图分类。"""
        ...


class ModerationClassification(str, Enum):
    """内容审核分类标签——与 content_moderation/main.py 的 ModerationCategory 对齐。"""

    POLITICS = "politics"
    ABUSE = "abuse"
    SPAM = "spam"
    PII = "pii"
    CLEAN = "clean"


class ContentClassifier(ABC):
    """内容审核分类器协议。

    输入待审核文本，输出违规分类结果 + 处置建议。
    实现方需覆盖 classify() 方法。
    """

    @abstractmethod
    def classify(self, content: str) -> ClassificationResult:
        """对待审核文本进行违规分类。"""
        ...
