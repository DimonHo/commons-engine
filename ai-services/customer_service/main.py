"""Commons Engine AI 服务层 — 智能客服。

阶段 2 MVP：基于关键词路由的 FAQ 客服，复杂问题标记 needsHuman 转人工。
后续阶段接入 NLP 模型（架构文档 3.8 节），模型层抽象，不锁定单一供应商。
"""
from __future__ import annotations

import logging
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from common.logging import configure_logging
from common.models import ApiResponse, HealthResponse

logger = configure_logging(service_name="customer-service")
app = FastAPI(
    title="Commons Engine · Customer Service",
    description="智能客服：自动处理常见咨询，复杂问题转人工",
    version="0.1.0",
)


# ── 请求 / 响应模型 ──────────────────────────────────────


class ChatRequest(BaseModel):
    """客服对话请求。"""

    message: str = Field(description="用户输入的咨询文本")
    user_id: str | None = Field(default=None, description="消费者/劳动者 ID，用于上下文")


class ChatReply(BaseModel):
    """客服对话单条回复。"""

    reply: str
    needs_human: bool = Field(
        default=False,
        description="是否需要转人工——当命中关键词无法处理或显式请求人工时为 True",
    )
    category: str | None = Field(default=None, description="命中的 FAQ 分类")


# ── FAQ 关键词路由表（阶段 2 MVP） ──────────────────────
# 设计说明：MVP 阶段用关键词命中做意图识别。接入 NLP 模型后，
# 此表作为 fallback / 冷启动兜底保留。
_FAQ_RULES: list[tuple[list[str], str, str]] = [
    (
        ["抽成", "佣金", "手续费", "平台费"],
        "category_commission",
        "公地引擎的抽成比例由合作社全体大会制定，所有资金流向公开透明。"
        "具体比例请查阅本合作社的治理细则或联系人工客服。",
    ),
    (
        ["评价", "评分", "信用", "星级"],
        "category_rating",
        "公地引擎采用双向评价——劳动者评价用户，用户评价劳动者。"
        "评价不直接挂钩接单资格，仅作参考。",
    ),
    (
        ["退款", "退钱", "投诉"],
        "category_refund",
        "关于退款/投诉，请提供订单号。简单纠纷 AI 可协助，复杂纠纷将转仲裁委员会。",
    ),
    (
        ["人工", "客服", "转人工", "真人"],
        "category_human",
        "好的，已为您转接人工客服，请稍候。",
    ),
]


def _classify(message: str) -> tuple[str | None, str, bool]:
    """返回 (category, reply, needs_human)。category 为 None 表示未命中已知意图。"""
    text = message.lower()
    for keywords, category, reply in _FAQ_RULES:
        if any(kw in text for kw in keywords):
            needs_human = category == "category_human"
            return category, reply, needs_human
    # 未命中任何关键词 → 转人工
    return None, "抱歉，我暂时无法理解您的问题，已为您转接人工客服。", True


# ── 端点 ────────────────────────────────────────────────


@app.get("/health", response_model=HealthResponse, tags=["ops"])
async def health() -> HealthResponse:
    """健康检查 — 契约与 Kotlin 侧 PlatformHealthController 对齐。"""
    return HealthResponse(service="customer-service")


@app.post(
    "/api/v1/customer-service/chat",
    response_model=ApiResponse[ChatReply],
    tags=["chat"],
)
async def chat(req: ChatRequest) -> ApiResponse[ChatReply]:
    """智能客服对话。

    MVP 实现基于关键词路由；后续接入 NLP 模型后此端点签名不变。
    """
    category, reply, needs_human = _classify(req.message)
    logger.info(
        "chat processed user_id=%s category=%s needs_human=%s",
        req.user_id,
        category,
        needs_human,
    )
    return ApiResponse(
        data=ChatReply(reply=reply, needs_human=needs_human, category=category)
    )
