"""内容审核端到端测试。"""
from __future__ import annotations

from fastapi.testclient import TestClient

from content_moderation.main import app

client = TestClient(app)


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["service"] == "content-moderation"


def test_clean_content_approved():
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "司机师傅服务很好，准时到达", "source": "rating"},
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["decision"] == "approved"
    assert data["category"] == "clean"


def test_spam_keyword_flagged():
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "加微信领取优惠", "source": "rating"},
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["decision"] == "flagged"
    assert data["category"] == "spam"


def test_phone_number_pii_flagged():
    """明文手机号应被标记——隐私保护（架构文档 1.5 数据主权）。"""
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "联系我 13800138000", "source": "rating"},
    )
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["decision"] == "flagged"
    assert data["category"] == "pii"


def test_id_card_pii_flagged():
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "身份证 110101199001011234"},
    )
    assert r.status_code == 200
    assert r.json()["data"]["category"] == "pii"


def test_merchant_info_source():
    """source 字段可正确传入且不报错。"""
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "本店经营餐饮", "source": "merchant_info"},
    )
    assert r.status_code == 200
    assert r.json()["data"]["decision"] == "approved"


def test_reason_is_explainable():
    """架构文档「算法透明」原则——每个判定必须附理由。"""
    r = client.post(
        "/api/v1/content-moderation/moderate",
        json={"content": "加微信"},
    )
    reason = r.json()["data"]["reason"]
    assert isinstance(reason, str) and len(reason) > 0
