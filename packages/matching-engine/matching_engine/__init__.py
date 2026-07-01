"""公地引擎 · 匹配引擎模块。

领域模型 + 可配置策略 + 可解释匹配。详见模块 README。
"""

from __future__ import annotations

from .geo import haversine_m
from .matcher import MatchingEngine
from .models import GeoPoint, Match, Order, Worker
from .strategies import (
    DistanceFirstStrategy,
    FairRoundRobinStrategy,
    RookieProtectionStrategy,
    MAX_MATCH_RADIUS_M,
)

__all__ = [
    "GeoPoint",
    "Order",
    "Worker",
    "Match",
    "MatchingEngine",
    "DistanceFirstStrategy",
    "FairRoundRobinStrategy",
    "RookieProtectionStrategy",
    "haversine_m",
    "MAX_MATCH_RADIUS_M",
]

__version__ = "0.0.1"
