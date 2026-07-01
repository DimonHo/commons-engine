from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_healthz_ok() -> None:
    resp = client.get("/healthz")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"


def test_match_picks_nearest_worker() -> None:
    resp = client.post(
        "/match",
        params={"order_id": "o1", "lat": 31.2304, "lon": 121.4737},  # 上海人民广场
        json=[
            {"worker_id": "far", "lat": 31.40, "lon": 121.50},
            {"worker_id": "near", "lat": 31.2310, "lon": 121.4740},
        ],
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["matched"] is True
    assert body["worker_id"] == "near"


def test_match_no_worker_in_range() -> None:
    resp = client.post(
        "/match",
        params={"order_id": "o2", "lat": 31.2304, "lon": 121.4737},
        json=[{"worker_id": "w", "lat": 40.0, "lon": 116.0}],  # 北京，远超半径
    )
    assert resp.status_code == 200
    assert resp.json()["matched"] is False
