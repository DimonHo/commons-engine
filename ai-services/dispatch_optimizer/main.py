"""Commons Engine AI 服务层 — 调度优化。

阶段 2 MVP：基于规则的派单建议——就近匹配 + 负载均衡，输出可解释的建议
（满足架构文档 3.5「路径优化服务于劳动者效率」与「算法透明」原则）。
后续阶段接入运筹优化模型，端点签名不变。
"""
from __future__ import annotations

import logging
import math
from typing import Any

from fastapi import FastAPI
from pydantic import BaseModel, Field

from common.logging import configure_logging
from common.models import ApiResponse, HealthResponse

logger = configure_logging(service_name="dispatch-optimizer")
app = FastAPI(
    title="Commons Engine · Dispatch Optimizer",
    description="调度优化：基于实时数据的路径和供需优化建议",
    version="0.1.0",
)


# ── 领域模型 ────────────────────────────────────────────


class WorkerLocation(BaseModel):
    """劳动者实时位置 + 状态——与 Kotlin 侧 Model.Worker 对齐。"""

    worker_id: str
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    active_order_count: int = Field(default=0, ge=0, description="当前进行中订单数")


class DispatchRequest(BaseModel):
    """调度建议请求。"""

    pickup_lat: float = Field(ge=-90, le=90, description="取件/上车点纬度")
    pickup_lng: float = Field(ge=-180, le=180, description="取件/上车点经度")
    candidates: list[WorkerLocation] = Field(min_length=0, description="候选劳动者列表")
    max_distance_meters: int = Field(default=5000, gt=0, description="最大匹配半径（米）")


class DispatchSuggestion(BaseModel):
    """单个调度建议。"""

    worker_id: str
    distance_meters: float
    score: float = Field(description="综合评分（越高越优先）")
    reason: str = Field(description="可解释的派单依据——满足算法透明原则")


class DispatchResult(BaseModel):
    """调度建议结果——已按评分降序排列。"""

    suggestions: list[DispatchSuggestion]
    strategy: str = Field(default="nearest_balanced", description="所用策略标识")


# ── 距离与评分 ──────────────────────────────────────────


def _haversine_meters(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Haversine 公式计算两点间距离（米）——与 Kotlin 侧 GeoUtils 对齐。"""
    r = 6_371_000.0  # 地球半径（米）
    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)
    d_phi = math.radians(lat2 - lat1)
    d_lambda = math.radians(lng2 - lng1)
    a = (
        math.sin(d_phi / 2) ** 2
        + math.cos(phi1) * math.cos(phi2) * math.sin(d_lambda / 2) ** 2
    )
    return 2 * r * math.asin(math.sqrt(a))


def _score(distance_meters: float, active_orders: int) -> float:
    """综合评分 = 距离因子 - 负载惩罚。

    - 距离越近分越高
    - 当前订单越多（负载越重）分越低——避免饥饿调度，保护劳动者不过劳
    """
    distance_factor = 1.0 / (1.0 + distance_meters / 1000.0)  # 距离归一化
    load_penalty = 0.15 * active_orders
    return round(distance_factor - load_penalty, 4)


# ── 端点 ────────────────────────────────────────────────


@app.get("/health", response_model=HealthResponse, tags=["ops"])
async def health() -> HealthResponse:
    return HealthResponse(service="dispatch-optimizer")


@app.post(
    "/api/v1/dispatch-optimizer/suggest",
    response_model=ApiResponse[DispatchResult],
    tags=["dispatch"],
)
async def suggest(req: DispatchRequest) -> ApiResponse[DispatchResult]:
    """生成派单建议。

    MVP 策略 nearest_balanced：就近匹配 + 负载均衡。
    返回按综合评分降序排列的候选劳动者列表，含可解释依据。
    """
    suggestions: list[DispatchSuggestion] = []
    for w in req.candidates:
        dist = _haversine_meters(req.pickup_lat, req.pickup_lng, w.lat, w.lng)
        if dist > req.max_distance_meters:
            continue
        s = _score(dist, w.active_order_count)
        suggestions.append(
            DispatchSuggestion(
                worker_id=w.worker_id,
                distance_meters=round(dist, 1),
                score=s,
                reason=f"距离 {dist:.0f}m，当前 {w.active_order_count} 单，评分 {s}",
            )
        )

    suggestions.sort(key=lambda x: x.score, reverse=True)
    logger.info(
        "dispatch suggestions: %d candidates -> %d in range",
        len(req.candidates),
        len(suggestions),
    )
    return ApiResponse(
        data=DispatchResult(suggestions=suggestions, strategy="nearest_balanced")
    )
