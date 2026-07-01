"""可配置匹配策略。

每种策略实现同一接口，合作社可按区域切换派单规则——引擎不硬编码策略。
所有策略都遵守反榨取约束 MAX_MATCH_RADIUS_M（拒绝超长空驶派单）。
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional, Protocol

from .geo import haversine_m
from .models import GeoPoint, Match, Order, Worker

# 反榨取约束：最大匹配半径（米）。超过该半径的候选一律不派，
# 防止系统性压低工资的超长空驶。最终值属治理事项（见 RFC #17）。
MAX_MATCH_RADIUS_M = 5_000.0


class DistanceFn(Protocol):
    def __call__(self, a: GeoPoint, b: GeoPoint) -> float: ...


class MatchingStrategy(Protocol):
    name: str

    def select(
        self, order: Order, workers: list[Worker], dist: DistanceFn
    ) -> Optional[Match]:
        ...


def _in_range(
    order: Order, workers: list[Worker], dist: DistanceFn, max_radius: float
) -> list[tuple[Worker, float]]:
    return [(w, d) for w in workers if (d := dist(order.origin, w.location)) <= max_radius]


@dataclass
class DistanceFirstStrategy:
    """距离优先：派给最近的在岗劳动者。"""

    name: str = "distance-first"
    max_radius_m: float = MAX_MATCH_RADIUS_M

    def select(self, order, workers, dist):
        candidates = _in_range(order, workers, dist, self.max_radius_m)
        if not candidates:
            return None
        candidates.sort(key=lambda x: x[1])
        w, d = candidates[0]
        return Match(
            order.id, w.id, d,
            (f"距离优先：你是范围内最近的劳动者（{d:.0f}m，上限 {self.max_radius_m:.0f}m）",),
        )


@dataclass
class FairRoundRobinStrategy:
    """公平轮转：按 id 顺序轮流派单，避免少数人吃掉所有单。"""

    name: str = "fair-round-robin"
    max_radius_m: float = MAX_MATCH_RADIUS_M
    _cursor: dict = field(default_factory=lambda: {"i": 0})

    def select(self, order, workers, dist):
        candidates = _in_range(order, workers, dist, self.max_radius_m)
        if not candidates:
            return None
        candidates.sort(key=lambda x: x[0].id)
        idx = self._cursor["i"] % len(candidates)
        w, d = candidates[idx]
        self._cursor["i"] = idx + 1
        return Match(
            order.id, w.id, d,
            (
                "公平轮转：按顺序派单，本轮轮到你",
                f"距离 {d:.0f}m（范围内 {len(candidates)} 人）",
            ),
        )


@dataclass
class RookieProtectionStrategy:
    """新人保护：优先把单派给接单较少的新劳动者，帮其起步。"""

    name: str = "rookie-protection"
    max_radius_m: float = MAX_MATCH_RADIUS_M
    rookie_threshold: int = 10  # 完成单低于此数视为新人

    def select(self, order, workers, dist):
        candidates = _in_range(order, workers, dist, self.max_radius_m)
        if not candidates:
            return None
        rookies = [w for w, _ in candidates if w.completed_orders < self.rookie_threshold]
        pool = rookies or [w for w, _ in candidates]
        pool.sort(key=lambda w: dist(order.origin, w.location))
        w = pool[0]
        d = dist(order.origin, w.location)
        if rookies:
            reason = "新人保护：范围内有新人，优先派给你以帮你起步"
        else:
            reason = f"范围内暂无新人，按距离派单（{d:.0f}m）"
        return Match(order.id, w.id, d, (reason,))
