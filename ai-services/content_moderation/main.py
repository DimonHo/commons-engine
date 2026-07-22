"""Commons Engine AI 服务层 — 内容审核。

阶段 2 MVP：基于规则词典的内容审核，覆盖评价文本、商家信息的常见违规类别
（涉政、辱骂、广告引流、敏感个人信息）。
后续阶段接入 NLP 分类模型（架构文档 3.8），规则引擎作为兜底 + 可解释依据。
"""
from __future__ import annotations

from enum import Enum
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from common.logging import configure_logging
from common.models import ApiResponse, HealthResponse

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


# ── 规则词典（阶段 2 MVP） ─────────────────────────────
# 说明：MVP 用关键词命中。词汇表刻意保守——宁可漏判转人工，不可误伤普通用户。
# 接入 NLP 模型后，词典作为 fallback + 可解释性依据保留。
_RULES: list[tuple[list[str], ModerationCategory, ModerationDecision]] = [
    (["辱骂占位"], ModerationCategory.ABUSE, ModerationDecision.BLOCKED),  # 占位，实际词典由治理配置
    (["加微信", "加v信", "微信号", "扫码进群", "私聊"], ModerationCategory.SPAM, ModerationDecision.FLAGGED),
]

# PII 检测：中国大陆手机号（11 位、1 开头）与身份证号（18 位）模式
import re

_PHONE_RE = re.compile(r"1[3-9]\d{9}")
_ID_CARD_RE = re.compile(r"\d{17}[\dXx]")


def _moderate(content: str, source: ContentSource) -> ModerationResult:
    """核心审核逻辑 — 规则引擎实现。"""
    text = content.lower()

    # 1. PII 检测（所有来源都拦截明文手机号/身份证）
    if _PHONE_RE.search(content) or _ID_CARD_RE.search(content):
        return ModerationResult(
            decision=ModerationDecision.FLAGGED,
            category=ModerationCategory.PII,
            confidence=0.95,
            reason="检测到疑似手机号或身份证号——为保护隐私，标记待复审",
        )

    # 2. 关键词规则
    for keywords, category, decision in _RULES:
        if any(kw in text for kw in keywords):
            return ModerationResult(
                decision=decision,
                category=category,
                confidence=0.8,
                reason=f"命中规则词典：{category.value}",
            )

    # 3. 默认通过
    return ModerationResult(
        decision=ModerationDecision.APPROVED,
        category=ModerationCategory.CLEAN,
        confidence=0.7,
        reason="未命中任何违规规则",
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
    flagged/blocked 内容由核心业务层（dispute/审核队列）做后续处理。
    """
    result = _moderate(req.content, req.source)
    logger.info(
        "moderated source=%s decision=%s category=%s confidence=%.2f",
        req.source.value,
        result.decision.value,
        result.category.value,
        result.confidence,
    )
    return ApiResponse(data=result)
