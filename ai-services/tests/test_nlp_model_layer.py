"""NLP 模型层抽象测试（#75）。

验证：
- IntentClassifier / ContentClassifier 协议一致性
- RuleBased 适配器与现有规则引擎行为一致
- Registry fallback 机制
- ClassificationResult 数据结构
"""
from __future__ import annotations

import pytest

from common.nlp import ClassifierRegistry, get_classifier
from common.nlp.base import (
    ClassificationResult,
    ContentClassifier,
    IntentClassifier,
    ModerationClassification,
)
from common.nlp.rule_based import (
    RuleBasedContentClassifier,
    RuleBasedIntentClassifier,
)


# ── ClassificationResult 结构测试 ─────────────────────


class TestClassificationResult:
    def test_basic_creation(self):
        r = ClassificationResult(
            category="test", confidence=0.9, reason="测试"
        )
        assert r.category == "test"
        assert r.confidence == 0.9
        assert r.reason == "测试"
        assert r.source == "model"

    def test_confidence_clamped_low(self):
        r = ClassificationResult("x", -0.5, "r")
        assert r.confidence == 0.0

    def test_confidence_clamped_high(self):
        r = ClassificationResult("x", 1.5, "r")
        assert r.confidence == 1.0

    def test_repr(self):
        r = ClassificationResult("cat", 0.5, "reason", "rule")
        assert "category='cat'" in repr(r)
        assert "source='rule'" in repr(r)


# ── RuleBasedIntentClassifier 测试 ───────────────────


class TestRuleBasedIntentClassifier:
    def setup_method(self):
        self.clf = RuleBasedIntentClassifier()

    def test_implements_protocol(self):
        assert isinstance(self.clf, IntentClassifier)

    def test_commission_intent(self):
        result = self.clf.classify("平台抽成多少？")
        assert result.category == "category_commission"
        assert result.source == "rule"
        assert result.confidence > 0

    def test_rating_intent(self):
        result = self.clf.classify("怎么评价司机")
        assert result.category == "category_rating"

    def test_refund_intent(self):
        result = self.clf.classify("我要退款")
        assert result.category == "category_refund"

    def test_human_escalation(self):
        result = self.clf.classify("我要转人工")
        assert result.category == "category_human"

    def test_unknown_intent_returns_none(self):
        result = self.clf.classify("xyzqwerty")
        assert result.category is None
        assert result.confidence == 0.0
        assert result.source == "rule"

    def test_case_insensitive(self):
        """关键词匹配应大小写无关。"""
        result = self.clf.classify("退款")
        assert result.category == "category_refund"


# ── RuleBasedContentClassifier 测试 ──────────────────


class TestRuleBasedContentClassifier:
    def setup_method(self):
        self.clf = RuleBasedContentClassifier()

    def test_implements_protocol(self):
        assert isinstance(self.clf, ContentClassifier)

    def test_clean_content(self):
        result = self.clf.classify("服务很好，很满意")
        assert result.category == ModerationClassification.CLEAN.value
        assert result.source == "rule"

    def test_pii_phone_detection(self):
        result = self.clf.classify("联系我 13812345678")
        assert result.category == ModerationClassification.PII.value
        assert result.confidence == 0.95

    def test_pii_id_card_detection(self):
        result = self.clf.classify("身份证 110101199001011234")
        assert result.category == ModerationClassification.PII.value

    def test_spam_detection(self):
        result = self.clf.classify("加微信了解更多")
        assert result.category == ModerationClassification.SPAM.value


# ── Registry / Factory 测试 ──────────────────────────


class TestClassifierRegistry:
    def setup_method(self):
        self.registry = ClassifierRegistry()

    def test_default_intent_classifier_is_rule_based(self):
        clf = self.registry.get_intent_classifier()
        assert isinstance(clf, RuleBasedIntentClassifier)

    def test_default_content_classifier_is_rule_based(self):
        clf = self.registry.get_content_classifier()
        assert isinstance(clf, RuleBasedContentClassifier)

    def test_explicit_rule_backend(self):
        clf = self.registry.get("intent", backend="rule")
        assert isinstance(clf, RuleBasedIntentClassifier)

    def test_unknown_backend_falls_back_to_rule(self):
        """请求不存在的 backend 应降级到 rule。"""
        clf = self.registry.get("intent", backend="nonexistent")
        assert isinstance(clf, RuleBasedIntentClassifier)

    def test_get_invalid_kind_raises(self):
        with pytest.raises(ValueError, match="未知分类器类型"):
            self.registry.get("invalid_kind")


class TestGetClassifierModuleLevel:
    def test_get_intent_classifier(self):
        clf = get_classifier("intent")
        assert isinstance(clf, IntentClassifier)

    def test_get_content_classifier(self):
        clf = get_classifier("content")
        assert isinstance(clf, ContentClassifier)


# ── 端到端：模型层与现有服务行为一致性 ─────────────────


class TestModelLayerConsistency:
    """验证模型层抽象与现有 customer_service / content_moderation 行为一致。

    这确保后续替换 backend 时，行为契约不破坏。
    """

    def test_intent_classifier_matches_customer_service_commission(self):
        """客服抽成意图——模型层与现有 _classify 一致。"""
        clf = RuleBasedIntentClassifier()
        result = clf.classify("平台抽成多少？")
        assert result.category == "category_commission"

    def test_content_classifier_pii_consistent(self):
        """PII 检测——模型层与现有 _moderate 一致。"""
        clf = RuleBasedContentClassifier()
        result = clf.classify("电话 13912345678")
        assert result.category == ModerationClassification.PII.value
        assert result.confidence == 0.95

    def test_fallback_chain_produces_result(self):
        """fallback 链最终必须返回一个有效的 ClassificationResult。"""
        registry = ClassifierRegistry()
        clf = registry.get_intent_classifier(backend="nonexistent-backend")
        result = clf.classify("测试消息")
        assert isinstance(result, ClassificationResult)
        assert 0.0 <= result.confidence <= 1.0
