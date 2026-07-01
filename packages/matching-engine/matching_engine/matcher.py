"""匹配引擎：注入可配置策略，对需求与供给匹配，产出可解释结果。"""

from __future__ import annotations

from typing import Optional, Sequence

from .geo import haversine_m
from .models import Match, Order, Worker
from .strategies import DistanceFirstStrategy, MatchingStrategy


class MatchingEngine:
    """匹配引擎核心。

    通过依赖注入策略实现"算法可配置"：合作社可按区域切换派单规则，
    引擎本身不硬编码策略。所有策略共享反榨取约束（见各策略 max_radius_m）。
    """

    def __init__(self, strategy: Optional[MatchingStrategy] = None) -> None:
        self.strategy = strategy or DistanceFirstStrategy()

    def match(self, order: Order, workers: Sequence[Worker]) -> Optional[Match]:
        """对一次需求在候选劳动者中匹配。无合适候选返回 None。"""
        return self.strategy.select(order, list(workers), haversine_m)
