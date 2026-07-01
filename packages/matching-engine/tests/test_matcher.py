"""匹配引擎单元测试。"""

from __future__ import annotations

import pytest

from matching_engine import (
    DistanceFirstStrategy,
    FairRoundRobinStrategy,
    GeoPoint,
    MatchingEngine,
    Order,
    RookieProtectionStrategy,
    Worker,
)


# ── 测试夹具：人民广场 ──
P = GeoPoint(31.2304, 121.4737)


def _worker(wid: str, *, dlat: float = 0.0, dlon: float = 0.0, **kw) -> Worker:
    return Worker(id=wid, location=GeoPoint(P.lat + dlat, P.lon + dlon), **kw)


# ── 距离优先 ──
def test_distance_first_picks_nearest():
    engine = MatchingEngine(DistanceFirstStrategy())
    result = engine.match(
        Order("o", P),
        [_worker("far", dlat=0.1, dlon=0.1), _worker("near", dlat=0.001, dlon=0.0)],
    )
    assert result is not None
    assert result.worker_id == "near"


def test_distance_first_explanation_readable():
    engine = MatchingEngine(DistanceFirstStrategy())
    result = engine.match(Order("o", P), [_worker("w", dlat=0.001, dlon=0.0)])
    assert result is not None
    assert "距离优先" in result.explanation
    assert "为什么把单 #o 派给你" in result.explanation


# ── 反榨取：超半径不派单 ──
def test_no_match_beyond_anti_exploitation_radius():
    engine = MatchingEngine(DistanceFirstStrategy())
    # 北京，距上海人民广场约 1000+ km，远超 5km 半径
    far = [Worker(id="bj", location=GeoPoint(39.9042, 116.4074))]
    assert engine.match(Order("o", P), far) is None


# ── 公平轮转 ──
def test_fair_round_robin_rotates():
    strategy = FairRoundRobinStrategy()
    engine = MatchingEngine(strategy)
    workers = [
        _worker("a", dlat=0.001, dlon=0.0),
        _worker("b", dlat=0.001, dlon=0.0),
        _worker("c", dlat=0.001, dlon=0.0),
    ]
    assigned = [engine.match(Order("o", P), workers).worker_id for _ in range(6)]
    # 应当按 a,b,c,a,b,c 轮转
    assert assigned == ["a", "b", "c", "a", "b", "c"]


# ── 新人保护 ──
def test_rookie_protection_prefers_newcomer():
    engine = MatchingEngine(RookieProtectionStrategy())
    veteran = _worker("veteran", dlat=0.001, dlon=0.0, completed_orders=999)
    rookie = _worker("rookie", dlat=0.002, dlon=0.0, completed_orders=1)
    result = engine.match(Order("o", P), [veteran, rookie])
    assert result is not None
    assert result.worker_id == "rookie"
    assert "新人保护" in result.reasons[0]


# ── 默认策略 ──
def test_default_strategy_is_distance_first():
    engine = MatchingEngine()
    assert engine.strategy.name == "distance-first"


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
