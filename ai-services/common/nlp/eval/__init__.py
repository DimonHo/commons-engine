"""NLP 分类器评估框架（#75 phase 3）。

评估 IntentClassifier / ContentClassifier 在标注数据集上的表现：
- accuracy（整体准确率）
- precision / recall / F1（每类）
- confusion matrix（混淆矩阵）

用法：
    from common.nlp.eval import evaluate_classifier, load_dataset

    classifier = get_classifier("intent")
    dataset = load_dataset("intent")
    report = evaluate_classifier(classifier, dataset)
    print(report.summary())
    print(report.per_class_report())

数据格式：JSONL，每行 {"text": "...", "label": "category_xxx"}
"""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from common.nlp.base import ClassificationResult, ContentClassifier, IntentClassifier

# ── 数据集路径 ──────────────────────────────────────────
_DATA_DIR = Path(__file__).parent.parent.parent.parent / "data" / "nlp"

_DATASET_MAP = {
    "intent": _DATA_DIR / "intent_train.jsonl",
    "content": _DATA_DIR / "content_train.jsonl",
}


@dataclass
class Sample:
    """单个标注样本。"""

    text: str
    label: str


@dataclass
class ClassMetrics:
    """单个类别的评估指标。"""

    label: str
    tp: int = 0  # true positive
    fp: int = 0  # false positive
    fn: int = 0  # false negative

    @property
    def precision(self) -> float:
        predicted = self.tp + self.fp
        return self.tp / predicted if predicted > 0 else 0.0

    @property
    def recall(self) -> float:
        actual = self.tp + self.fn
        return self.tp / actual if actual > 0 else 0.0

    @property
    def f1(self) -> float:
        p, r = self.precision, self.recall
        return 2 * p * r / (p + r) if (p + r) > 0 else 0.0


@dataclass
class EvalReport:
    """完整评估报告。"""

    classifier_name: str
    total: int = 0
    correct: int = 0
    class_metrics: dict[str, ClassMetrics] = field(default_factory=dict)
    confusion: dict[tuple[str, str], int] = field(default_factory=dict)
    errors: list[dict] = field(default_factory=list)

    @property
    def accuracy(self) -> float:
        return self.correct / self.total if self.total > 0 else 0.0

    def summary(self) -> str:
        """一行摘要。"""
        return (
            f"{self.classifier_name}: "
            f"accuracy={self.accuracy:.2%} "
            f"({self.correct}/{self.total}) "
            f"classes={len(self.class_metrics)}"
        )

    def per_class_report(self) -> str:
        """每类 P/R/F1 报告。"""
        lines = [f"{'label':<30} {'precision':>10} {'recall':>10} {'f1':>10}"]
        lines.append("-" * 64)
        for label in sorted(self.class_metrics):
            m = self.class_metrics[label]
            lines.append(
                f"{label:<30} {m.precision:>10.2%} {m.recall:>10.2%} {m.f1:>10.2%}"
            )
        return "\n".join(lines)

    def error_summary(self) -> str:
        """错分样本摘要（最多前 10 条）。"""
        if not self.errors:
            return "No errors."
        lines = [f"Errors ({len(self.errors)} total, showing first 10):"]
        for e in self.errors[:10]:
            lines.append(
                f"  '{e['text'][:40]}' expected={e['expected']} got={e['got']}"
            )
        return "\n".join(lines)


def load_dataset(name: str) -> list[Sample]:
    """加载标注数据集。

    Args:
        name: 'intent' 或 'content'，或自定义 JSONL 文件路径。
    """
    path = Path(name) if name.endswith(".jsonl") else _DATASET_MAP.get(name)
    if path is None or not path.exists():
        raise FileNotFoundError(f"数据集不存在: {name}（查找路径: {path}）")

    samples: list[Sample] = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            obj = json.loads(line)
            samples.append(Sample(text=obj["text"], label=obj["label"]))
    return samples


def evaluate_classifier(
    classifier: IntentClassifier | ContentClassifier,
    dataset: list[Sample],
) -> EvalReport:
    """在标注数据集上评估分类器。

    Args:
        classifier: 实现 classify() 方法的分类器实例
        dataset: 标注样本列表

    Returns:
        EvalReport 包含 accuracy / precision / recall / F1 / 混淆矩阵
    """
    report = EvalReport(classifier_name=classifier.__class__.__name__)

    for sample in dataset:
        result: ClassificationResult = classifier.classify(sample.text)
        predicted = result.category or "category_human"  # None → 转人工兜底

        report.total += 1

        # 混淆矩阵
        key = (sample.label, predicted)
        report.confusion[key] = report.confusion.get(key, 0) + 1

        # 初始化类别指标
        for label in (sample.label, predicted):
            if label not in report.class_metrics:
                report.class_metrics[label] = ClassMetrics(label=label)

        if predicted == sample.label:
            report.correct += 1
            report.class_metrics[sample.label].tp += 1
        else:
            report.class_metrics[predicted].fp += 1
            report.class_metrics[sample.label].fn += 1
            report.errors.append(
                {"text": sample.text, "expected": sample.label, "got": predicted}
            )

    return report
