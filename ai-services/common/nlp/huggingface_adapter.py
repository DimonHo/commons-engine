"""HuggingFace NLP 适配器 — 本地部署的开源文本分类模型。

数据主权优先：模型在本地推理，数据不离开进程。
依赖：transformers + torch（可选，按需安装）。

安装：
    pip install transformers torch

配置（环境变量）：
    COMMONS_INTENT_MODEL=bert-base-chinese  # 客服意图分类模型
    COMMONS_CONTENT_MODEL=bert-base-chinese # 内容审核分类模型

注意：当前为脚手架实现——需要微调后的模型才能实际推理。
生产使用需准备标注数据 → 微调 → 导出模型路径。
"""
from __future__ import annotations

import logging
import os

from common.nlp.base import (
    ClassificationResult,
    ContentClassifier,
    IntentClassifier,
    ModerationClassification,
)

logger = logging.getLogger("ai-services.nlp.huggingface")

# transformers 是可选依赖——导入时可能失败
try:
    from transformers import pipeline  # type: ignore[import-not-found]

    _HAS_TRANSFORMERS = True
except ImportError:
    _HAS_TRANSFORMERS = False
    pipeline = None  # type: ignore[assignment, misc]


class _BaseHuggingFaceClassifier:
    """HuggingFace 适配器公共逻辑。"""

    def __init__(
        self, model_name: str, labels: dict[str, str]
    ) -> None:
        if not _HAS_TRANSFORMERS:
            raise ImportError(
                "transformers 未安装。运行 pip install transformers torch 启用 NLP 模式。"
            )
        self._model_name = model_name
        self._labels = labels
        logger.info("加载 HuggingFace 模型: %s", model_name)
        # 使用 zero-shot-classification pipeline 作为通用方案
        # 生产环境可替换为微调后的专用模型路径
        self._pipe = pipeline(  # type: ignore[misc]
            "zero-shot-classification", model=model_name
        )

    def _predict(self, text: str, candidate_labels: list[str]) -> ClassificationResult:
        result = self._pipe(text, candidate_labels=candidate_labels)  # type: ignore[misc]
        top_label = result["labels"][0]
        top_score = result["scores"][0]
        mapped = self._labels.get(top_label, top_label)
        return ClassificationResult(
            category=mapped,
            confidence=top_score,
            reason=f"HuggingFace 零样本分类: model={self._model_name}, label={top_label}",
            source="model",
        )


class HuggingFaceIntentClassifier(_BaseHuggingFaceClassifier, IntentClassifier):
    """客服意图分类——HuggingFace 零样本方案。

    零样本模式不需要微调数据，适合冷启动。
    生产环境建议用标注数据微调专用模型提升准确率。
    """

    # HuggingFace 候选标签 → 内部分类标签映射
    _LABELS = {
        "佣金抽成": "category_commission",
        "评价信用": "category_rating",
        "退款投诉": "category_refund",
        "转人工": "category_human",
    }

    def __init__(self) -> None:
        model = os.environ.get(
            "COMMONS_INTENT_MODEL", "MoritzLaurer/mDeBERTa-v3-base-mnli-xnli"
        )
        super().__init__(model, self._LABELS)

    def classify(self, message: str) -> ClassificationResult:
        candidate_labels = list(self._LABELS.keys())
        return self._predict(message, candidate_labels)


class HuggingFaceContentClassifier(_BaseHuggingFaceClassifier, ContentClassifier):
    """内容审核分类——HuggingFace 零样本方案。"""

    _LABELS = {
        "涉政敏感": ModerationClassification.POLITICS.value,
        "辱骂攻击": ModerationClassification.ABUSE.value,
        "广告引流": ModerationClassification.SPAM.value,
        "个人信息": ModerationClassification.PII.value,
        "正常内容": ModerationClassification.CLEAN.value,
    }

    def __init__(self) -> None:
        model = os.environ.get(
            "COMMONS_CONTENT_MODEL", "MoritzLaurer/mDeBERTa-v3-base-mnli-xnli"
        )
        super().__init__(model, self._LABELS)

    def classify(self, content: str) -> ClassificationResult:
        candidate_labels = list(self._LABELS.keys())
        return self._predict(content, candidate_labels)
