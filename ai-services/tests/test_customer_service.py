"""智能客服端到端测试。"""
from __future__ import annotations

from fastapi.testclient import TestClient

from customer_service.main import app

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "UP"
    assert body["service"] == "customer-service"


def test_chat_commission_faq():
    r = client.post(
        "/api/v1/customer-service/chat",
        json={"message": "平台抽成多少？", "user_id": "u1"},
    )
    assert r.status_code == 200
    body = r.json()
    assert body["success"] is True
    assert body["data"]["category"] == "category_commission"
    assert body["data"]["needs_human"] is False


def test_chat_rating_faq():
    r = client.post(
        "/api/v1/customer-service/chat",
        json={"message": "怎么评价司机"},
    )
    assert r.status_code == 200
    assert r.json()["data"]["category"] == "category_rating"


def test_chat_explicit_human_escalation():
    r = client.post(
        "/api/v1/customer-service/chat",
        json={"message": "我要转人工"},
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["category"] == "category_human"
    assert data["needs_human"] is True


def test_chat_unknown_intent_escalates_to_human():
    r = client.post(
        "/api/v1/customer-service/chat",
        json={"message": "xyzqwerty"},
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["needs_human"] is True
    assert data["category"] is None


def test_chat_refund_category():
    r = client.post(
        "/api/v1/customer-service/chat",
        json={"message": "我要退款"},
    )
    assert r.json()["data"]["category"] == "category_refund"


def test_api_response_envelope_shape():
    """验证统一响应包装结构（与 Kotlin 侧消费契约对齐）。"""
    r = client.post("/api/v1/customer-service/chat", json={"message": "抽成"})
    body = r.json()
    assert set(body.keys()) >= {"success", "data", "message"}
    assert set(body["data"].keys()) == {"reply", "needs_human", "category"}
