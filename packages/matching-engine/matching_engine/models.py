"""匹配引擎领域模型。"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class GeoPoint:
    """WGS84 地理坐标。"""

    lat: float
    lon: float


@dataclass(frozen=True)
class Order:
    """一次需求（乘客叫车、用户点单等）。"""

    id: str
    origin: GeoPoint


@dataclass(frozen=True)
class Worker:
    """劳动者供给（司机 / 骑手）。"""

    id: str
    location: GeoPoint
    rating: float = 5.0
    completed_orders: int = 0


@dataclass(frozen=True)
class Match:
    """一次匹配结果，附带可解释理由。"""

    order_id: str
    worker_id: str
    distance_m: float
    reasons: tuple[str, ...]

    @property
    def explanation(self) -> str:
        """对劳动者可读的派单理由（可审计）。"""
        lines = "\n".join(f"- {r}" for r in self.reasons)
        return f"为什么把单 #{self.order_id} 派给你（{self.worker_id}）：\n{lines}"
