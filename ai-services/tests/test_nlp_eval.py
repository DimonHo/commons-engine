"""评估框架的单元测试（#75 phase 3）。

验证：
1. 数据集加载正确
2. 规则引擎分类器在种子数据集上有合理的准确率
3. 指标计算正确（accuracy / precision / recall / F1）
4. 混淆矩阵正确
"""
from __future__ import annotations

from common.nlp.eval import (
    ClassMetrics,
    EvalReport,
    Sample,
    evaluate_classifier,
    load_dataset,
)
from common.nlp.rule_based import (
    RuleBasedContentClassifier,
    RuleBasedIntentClassifier,
)


class TestDatasetLoading:
    """数据集加载测试。"""

    def test_load_intent_dataset(self) -> None:
        ds = load_dataset("intent")
        assert len(ds) > 0
        assert all(isinstance(s, Sample) for s in ds)
        assert all(s.text and s.label for s in ds)

    def test_load_content_dataset(self) -> None:
        ds = load_dataset("content")
        assert len(ds) > 0
        assert all(isinstance(s, Sample) for s in ds)

    def test_intent_labels_valid(self) -> None:
        ds = load_dataset("intent")
        valid_labels = {
            "category_commission",
            "category_rating",
            "category_refund",
            "category_human",
        }
        for s in ds:
            assert s.label in valid_labels, f"Invalid label: {s.label}"

    def test_content_labels_valid(self) -> None:
        ds = load_dataset("content")
        valid_labels = {"clean", "spam", "pii", "politics", "abuse"}
        for s in ds:
            assert s.label in valid_labels, f"Invalid label: {s.label}"

    def test_nonexistent_dataset_raises(self) -> None:
        try:
            load_dataset("nonexistent_dataset")
            assert False, "Should have raised FileNotFoundError"
        except FileNotFoundError:
            pass


class TestClassMetrics:
    """指标计算测试。"""

    def test_perfect_classification(self) -> None:
        m = ClassMetrics(label="x", tp=10, fp=0, fn=0)
        assert m.precision == 1.0
        assert m.recall == 1.0
        assert m.f1 == 1.0

    def test_zero_predictions(self) -> None:
        m = ClassMetrics(label="x", tp=0, fp=0, fn=5)
        assert m.precision == 0.0
        assert m.recall == 0.0
        assert m.f1 == 0.0

    def test_partial_classification(self) -> None:
        m = ClassMetrics(label="x", tp=8, fp=2, fn=2)
        assert m.precision == 0.8
        assert m.recall == 0.8
        assert abs(m.f1 - 0.8) < 0.001


class TestEvaluateClassifier:
    """分类器评估测试。"""

    def test_intent_classifier_accuracy(self) -> None:
        """规则引擎在意图数据集上应有 > 60% 准确率。"""
        ds = load_dataset("intent")
        classifier = RuleBasedIntentClassifier()
        report = evaluate_classifier(classifier, ds)
        assert report.total == len(ds)
        assert report.accuracy > 0.6, (
            f"Rule-based intent accuracy too low: {report.accuracy:.2%}"
        )

    def test_content_classifier_accuracy(self) -> None:
        """规则引擎在内容审核数据集上应有 > 60% 准确率。"""
        ds = load_dataset("content")
        classifier = RuleBasedContentClassifier()
        report = evaluate_classifier(classifier, ds)
        assert report.total == len(ds)
        assert report.accuracy > 0.6, (
            f"Rule-based content accuracy too low: {report.accuracy:.2%}"
        )

    def test_report_has_class_metrics(self) -> None:
        ds = load_dataset("intent")
        classifier = RuleBasedIntentClassifier()
        report = evaluate_classifier(classifier, ds)
        assert len(report.class_metrics) > 0
        # 至少应该有期望标签的指标
        expected_labels = {s.label for s in ds}
        for label in expected_labels:
            assert label in report.class_metrics

    def test_confusion_matrix_populated(self) -> None:
        ds = load_dataset("intent")
        classifier = RuleBasedIntentClassifier()
        report = evaluate_classifier(classifier, ds)
        assert len(report.confusion) > 0
        # 对角线（正确预测）应存在
        correct = sum(
            count for (expected, got), count in report.confusion.items()
            if expected == got
        )
        assert correct == report.correct

    def test_summary_and_report_strings(self) -> None:
        ds = load_dataset("intent")
        classifier = RuleBasedIntentClassifier()
        report = evaluate_classifier(classifier, ds)
        summary = report.summary()
        per_class = report.per_class_report()
        assert "accuracy" in summary
        assert "precision" in per_class
        assert "recall" in per_class

    def test_errors_recorded(self) -> None:
        """有错分时应记录。"""
        ds = load_dataset("intent")
        classifier = RuleBasedIntentClassifier()
        report = evaluate_classifier(classifier, ds)
        if report.correct < report.total:
            assert len(report.errors) == report.total - report.correct
            err_summary = report.error_summary()
            assert "Errors" in err_summary or "No errors" in err_summary
