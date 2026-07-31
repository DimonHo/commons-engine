"""NLP 模型层服务集成测试（#75 phase 2）。

验证 customer_service 和 content_moderation 已正确接入 NLP registry：
- 默认 rule backend 下行为与原 MVP 完全一致（向后兼容）
- 服务内部使用 _intent_classifier / _content_classifier（registry 实例）
- backend 切换不影响端点契约
"""
from __future__ import annotations

import importlib

import pytest
from fastapi.testclient import TestClient


# ── 服务加载了 NLP registry ────────────────────────────


class TestServiceUsesRegistry:
    """验证两个服务都通过 registry 获取分类器实例。"""

    def test_customer_service_has_intent_classifier(self):
        """customer_service.main 应持有 _intent_classifier 实例。"""
        import customer_service.main as svc
        assert hasattr(svc, "_intent_classifier"), \
            "customer_service 应通过 registry 初始化 _intent_classifier"
        assert svc._intent_classifier is not None

    def test_content_moderation_has_content_classifier(self):
        """content_moderation.main 应持有 _content_classifier 实例。"""
        import content_moderation.main as svc
        assert hasattr(svc, "_content_classifier"), \
            "content_moderation 应通过 registry 初始化 _content_classifier"
        assert svc._content_classifier is not None

    def test_default_backend_is_rule_based(self):
        """默认 backend（无环境变量）应为 RuleBased 分类器。"""
        from common.nlp.rule_based import (
            RuleBasedContentClassifier,
            RuleBasedIntentClassifier,
        )
        import content_moderation.main as cm
        import customer_service.main as cs

        assert isinstance(cs._intent_classifier, RuleBasedIntentClassifier)
        assert isinstance(cm._content_classifier, RuleBasedContentClassifier)


# ── 向后兼容：端点行为不变 ─────────────────────────────


class TestCustomerServiceBackwardCompat:
    """集成测试：接入 registry 后，客服端点行为与 MVP 一致。"""

    @pytest.fixture
    def client(self):
        import customer_service.main as svc
        return TestClient(svc.app)

    def test_commission_intent_unchanged(self, client):
        """抽成意图 → category_commission，回复内容不变。"""
        resp = client.post(
            "/api/v1/customer-service/chat",
            json={"message": "平台抽成多少？"},
        )
        assert resp.status_code == 200
        data = resp.json()["data"]
        assert data["category"] == "category_commission"
        assert "抽成比例" in data["reply"]
        assert data["needs_human"] is False

    def test_human_escalation_unchanged(self, client):
        """转人工意图 → needs_human=True。"""
        resp = client.post(
            "/api/v1/customer-service/chat",
            json={"message": "我要转人工"},
        )
        data = resp.json()["data"]
        assert data["category"] == "category_human"
        assert data["needs_human"] is True

    def test_unknown_intent_routes_to_human(self, client):
        """未知意图 → 转人工兜底。"""
        resp = client.post(
            "/api/v1/customer-service/chat",
            json={"message": "xyzqwerty12345"},
        )
        data = resp.json()["data"]
        assert data["category"] is None
        assert data["needs_human"] is True

    def test_refund_intent_unchanged(self, client):
        """退款意图 → category_refund。"""
        resp = client.post(
            "/api/v1/customer-service/chat",
            json={"message": "我要退款"},
        )
        data = resp.json()["data"]
        assert data["category"] == "category_refund"


class TestContentModerationBackwardCompat:
    """集成测试：接入 registry 后，审核端点行为与 MVP 一致。"""

    @pytest.fixture
    def client(self):
        import content_moderation.main as svc
        return TestClient(svc.app)

    def test_clean_content_approved(self, client):
        """正常内容 → approved + clean。"""
        resp = client.post(
            "/api/v1/content-moderation/moderate",
            json={"content": "服务很好，很满意"},
        )
        data = resp.json()["data"]
        assert data["decision"] == "approved"
        assert data["category"] == "clean"

    def test_pii_phone_flagged(self, client):
        """手机号 PII → flagged。"""
        resp = client.post(
            "/api/v1/content-moderation/moderate",
            json={"content": "联系我 13812345678"},
        )
        data = resp.json()["data"]
        assert data["decision"] == "flagged"
        assert data["category"] == "pii"
        assert data["confidence"] == pytest.approx(0.95)

    def test_spam_flagged(self, client):
        """广告引流 → flagged + spam。"""
        resp = client.post(
            "/api/v1/content-moderation/moderate",
            json={"content": "加微信了解更多"},
        )
        data = resp.json()["data"]
        assert data["decision"] == "flagged"
        assert data["category"] == "spam"


# ── 单一事实源验证：规则不重复 ─────────────────────────


class TestSingleSourceOfTruth:
    """验证关键词规则只在 common/nlp/rule_based.py 维护，不在服务模块重复。"""

    def test_customer_service_no_keyword_rules(self):
        """customer_service.main 不应再硬编码关键词列表。"""
        import customer_service.main as svc
        assert not hasattr(svc, "_FAQ_RULES"), \
            "关键词规则应迁移到 common.nlp.rule_based，不应在服务模块重复"
        assert hasattr(svc, "_INTENT_REPLIES"), \
            "服务模块应只持有回复表 _INTENT_REPLIES"

    def test_content_moderation_no_keyword_rules(self):
        """content_moderation.main 不应再硬编码关键词列表。"""
        import content_moderation.main as svc
        assert not hasattr(svc, "_RULES"), \
            "关键词规则应迁移到 common.nlp.rule_based，不应在服务模块重复"
        assert not hasattr(svc, "_PHONE_RE"), \
            "PII 正则应迁移到 common.nlp.rule_based"
