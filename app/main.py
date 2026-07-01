"""公地引擎 — 应用入口。

当前为阶段 1 MVP 骨架：
- GET  /healthz  健康检查
- POST /match    匹配引擎 demo 端点（实时打车场景）

运行：
    uvicorn app.main:app --reload
"""

from __future__ import annotations

from fastapi import FastAPI
from pydantic import BaseModel, Field

from matching_engine import GeoPoint, MatchingEngine, Order, Worker

app = FastAPI(title="Commons Engine", version="0.0.1")
_engine = MatchingEngine()


class GeoIn(BaseModel):
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)


class OrderIn(BaseModel):
    order_id: str
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)


class WorkerIn(BaseModel):
    worker_id: str
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    rating: float = 5.0
    completed_orders: int = 0


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "ok", "service": "commons-engine", "version": app.version}


@app.post("/match")
def match(order: OrderIn, workers: list[WorkerIn]) -> dict:
    """对一次需求在候选劳动者中做匹配，返回匹配结果与可解释理由。"""
    o = Order(id=order.order_id, origin=GeoPoint(order.lat, order.lon))
    ws = [
        Worker(
            id=w.worker_id,
            location=GeoPoint(w.lat, w.lon),
            rating=w.rating,
            completed_orders=w.completed_orders,
        )
        for w in workers
    ]
    result = _engine.match(o, ws)
    if result is None:
        return {
            "matched": False,
            "explanation": "范围内无可用劳动者（反榨取半径保护：拒绝超长空驶派单）",
        }
    return {
        "matched": True,
        "worker_id": result.worker_id,
        "distance_m": round(result.distance_m, 1),
        "explanation": result.explanation,
    }
