#!/usr/bin/env python3
"""NLP 分类器评估 CLI（#75 phase 3）。

用法：
    PYTHONPATH=. python -m common.nlp.eval.run intent
    PYTHONPATH=. python -m common.nlp.eval.run content
    PYTHONPATH=. python -m common.nlp.eval.run intent --backend huggingface

输出：
    - 准确率摘要
    - 每类 P/R/F1 报告
    - 错分样本列表
"""
from __future__ import annotations

import sys

from common.nlp import get_classifier
from common.nlp.eval import evaluate_classifier, load_dataset


def main() -> int:
    if len(sys.argv) < 2:
        print("用法: python -m common.nlp.eval.run <intent|content> [--backend rule|huggingface]")
        return 1

    kind = sys.argv[1]
    backend = None
    if "--backend" in sys.argv:
        idx = sys.argv.index("--backend")
        backend = sys.argv[idx + 1] if idx + 1 < len(sys.argv) else None

    # 加载数据集
    try:
        dataset = load_dataset(kind)
    except FileNotFoundError as e:
        print(f"ERROR: {e}")
        return 1

    print(f"Dataset: {kind} ({len(dataset)} samples)")
    print(f"Backend: {backend or 'default'}")
    print()

    # 获取分类器
    classifier = get_classifier(kind, backend=backend)

    # 评估
    report = evaluate_classifier(classifier, dataset)

    print(report.summary())
    print()
    print(report.per_class_report())
    print()
    print(report.error_summary())

    return 0


if __name__ == "__main__":
    sys.exit(main())
