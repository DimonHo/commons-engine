"""调度优化端到端测试。"""
from __future__ import annotations

from fastapi.testclient import TestClient

from dispatch_optimizer.main import app, _haversine_meters, _score

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["service"] == "dispatch-optimizer"


def test_nearest_worker_ranked_first():
    """最近且无负载的劳动者应排在第一。"""
    r = client.post(
        "/api/v1/dispatch-optimizer/suggest",
        json={
            "pickup_lat": 39.9,
            "pickup_lng": 116.4,
            "candidates": [
                {"worker_id": "mid", "lat": 39.905, "lng": 116.405, "active_order_count": 0},
                {"worker_id": "near", "lat": 39.901, "lng": 116.401, "active_order_count": 0},
            ],
        },
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert len(data["suggestions"]) == 2
    assert data["suggestions"][0]["worker_id"] == "near"
    assert data["suggestions"][0]["distance_meters"] < data["suggestions"][1]["distance_meters"]


def test_load_penalty_affects_ranking():
    """距离相近时，负载低的劳动者应优先——避免过劳调度。"""
    r = client.post(
        "/api/v1/dispatch-optimizer/suggest",
        json={
            "pickup_lat": 39.9,
            "pickup_lng": 116.4,
            "candidates": [
                {"worker_id": "busy", "lat": 39.901, "lng": 116.401, "active_order_count": 5},
                {"worker_id": "free", "lat": 39.901, "lng": 116.401, "active_order_count": 0},
            ],
        },
    )
    data = r.json()["data"]
    assert data["suggestions"][0]["worker_id"] == "free"


def test_out_of_range_excluded():
    r = client.post(
        "/api/v1/dispatch-optimizer/suggest",
        json={
            "pickup_lat": 39.9,
            "pickup_lng": 116.4,
            "max_distance_meters": 100,
            "candidates": [
                {"worker_id": "far", "lat": 40.0, "lng": 116.5, "active_order_count": 0},
            ],
        },
    )
    assert r.json()["data"]["suggestions"] == []


def test_empty_candidates():
    r = client.post(
        "/api/v1/dispatch-optimizer/suggest",
        json={"pickup_lat": 39.9, "pickup_lng": 116.4, "candidates": []},
    )
    assert r.status_code == 200
    assert r.json()["data"]["suggestions"] == []


def test_reason_is_explainable():
    r = client.post(
        "/api/v1/dispatch-optimizer/suggest",
        json={
            "pickup_lat": 39.9,
            "pickup_lng": 116.4,
            "candidates": [
                {"worker_id": "w1", "lat": 39.901, "lng": 116.401, "active_order_count": 0},
            ],
        },
    )
    reason = r.json()["data"]["suggestions"][0]["reason"]
    assert "距离" in reason and "单" in reason


def test_haversine_matches_geoutils():
    """与 Kotlin 侧 GeoUtils 同一公式，验证已知距离。"""
    d = _haversine_meters(39.9, 116.4, 39.9, 116.4)
    assert d == 0.0
    # 北京同纬度跨 0.001 经度约 85m
    d2 = _haversine_meters(39.9, 116.4, 39.9, 116.401)
    assert 70 < d2 < 100


def test_score_monotonic_with_distance():
    assert _score(100, 0) > _score(1000, 0)
    assert _score(100, 0) > _score(100, 3)
