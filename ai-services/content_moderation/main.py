"""Commons Engine AI 服务层 — 内容审核。

阶段 2 MVP：基于规则词典的内容审核，覆盖评价文本、商家信息的常见违规类别
（涉政、辱骂、广告引流、敏感个人信息）。
阶段 2.1（#75）：接入 NLP 模型层抽象，审核分类由 registry 分发，
端点签名不变，规则引擎降级为 fallback + 可解释依据。
"""
from __future__ import annotations

from enum import Enum
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from common.logging import configure_logging
from common.models import ApiResponse, HealthResponse
from common.nlp import get_classifier
from common.nlp.base import ModerationClassification

logger = configure_logging(service_name="content-moderation")
app = FastAPI(
    title="Commons Engine · Content Moderation",
    description="内容审核：自动审核商家信息、用户评价中的违规内容",
    version="0.1.0",
)


# ── 领域模型 ────────────────────────────────────────────


class ModerationCategory(str, Enum):
    """违规类别 — 与 Kotlin 侧 dispute 模块的违规分类对齐。"""

    POLITICS = "politics"  # 涉政敏感
    ABUSE = "abuse"  # 辱骂攻击
    SPAM = "spam"  # 广告引流
    PII = "pii"  # 敏感个人信息（手机号/身份证）
    CLEAN = "clean"  # 无违规


class ModerationDecision(str, Enum):
    """处置决策。"""

    APPROVED = "approved"  # 通过
    FLAGGED = "flagged"  # 标记待复审
    BLOCKED = "blocked"  # 拦截


class ContentSource(str, Enum):
    """内容来源 — 决定审核严格度。"""

    RATING = "rating"  # 用户评价（双向）
    MERCHANT_INFO = "merchant_info"  # 商家信息
    PROFILE = "profile"  # 劳动者/用户个人资料


class ModerationRequest(BaseModel):
    """内容审核请求。"""

    content: str = Field(description="待审核文本")
    source: ContentSource = Field(default=ContentSource.RATING, description="内容来源")


class ModerationResult(BaseModel):
    """单次审核结果。"""

    decision: ModerationDecision
    category: ModerationCategory
    confidence: float = Field(ge=0.0, le=1.0, description="置信度 0-1")
    reason: str = Field(description="可解释的判定依据——满足架构文档「算法透明」原则")


# ── 违规类别 → 处置决策映射 ─────────────────────────────
# NLP 模型层（#75）返回分类标签，此处决定处置力度。
# 规则引擎的 keyword 词典已迁移到 common/nlp/rule_based.py，
# 此模块不再维护重复的关键词列表。
_CATEGORY_DECISION: dict[str, ModerationDecision] = {
    ModerationCategory.POLITICS.value: ModerationDecision.BLOCKED,
    ModerationCategory.ABUSE.value: ModerationDecision.BLOCKED,
    ModerationCategory.SPAM.value: ModerationDecision.FLAGGED,
    ModerationCategory.PII.value: ModerationDecision.FLAGGED,
    ModerationCategory.CLEAN.value: ModerationDecision.APPROVED,
}

# ── NLP 内容审核分类器（#75：通过 registry 选择后端） ──
# 默认 rule backend = 与原规则词典行为完全一致。
# 设置 COMMONS_CONTENT_CLASSIFIER=huggingface 切换 NLP 模型。
_content_classifier = get_classifier("content")


def _moderate(content: str, source: ContentSource) -> ModerationResult:
    """核心审核逻辑 — 通过 NLP 模型层 registry 分发（#75）。"""
    result = _content_classifier.classify(content)
    category = result.category or ModerationCategory.CLEAN.value
    decision = _CATEGORY_DECISION.get(category, ModerationDecision.FLAGGED)

    # 将分类标签映射回枚举值——ModerationCategory 是 str+Enum，支持按值构造
    try:
        moderation_category = ModerationCategory(category)
    except ValueError:
        # 未知分类 → 标记待复审（保守策略）
        moderation_category = ModerationCategory.CLEAN
        decision = ModerationDecision.FLAGGED

    return ModerationResult(
        decision=decision,
        category=moderation_category,
        confidence=result.confidence,
        reason=result.reason,
    )


# ── 端点 ────────────────────────────────────────────────


@app.get("/health", response_model=HealthResponse, tags=["ops"])
async def health() -> HealthResponse:
    return HealthResponse(service="content-moderation")


@app.post(
    "/api/v1/content-moderation/moderate",
    response_model=ApiResponse[ModerationResult],
    tags=["moderation"],
)
async def moderate(req: ModerationRequest) -> ApiResponse[ModerationResult]:
    """审核单条文本内容。

    返回决策（approved/flagged/blocked）+ 可解释依据。
    审核分类通过 NLP 模型层 registry 分发（#75）：
    - 默认 rule backend（规则词典，与 MVP 行为一致）
    - 设置 COMMONS_CONTENT_CLASSIFIER=huggingface 切换 NLP 模型
    flagged/blocked 内容由核心业务层（dispute/审核队列）做后续处理。
    """
    result = _moderate(req.content, req.source)
    logger.info(
        "moderated source=%s decision=%s category=%s confidence=%.2f backend=%s",
        req.source.value,
        result.decision.value,
        result.category.value,
        result.confidence,
        _content_classifier.__class__.__name__,
    )
    return ApiResponse(data=result)
