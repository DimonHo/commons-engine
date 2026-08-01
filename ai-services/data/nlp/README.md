# NLP 标注数据集与评估框架

> Issue #75 phase 3：为 NLP 模型微调准备标注数据 + 基准评估能力。

## 目录结构

```
ai-services/
├── common/nlp/eval/
│   ├── __init__.py          # 评估框架核心（EvalReport, evaluate_classifier, load_dataset）
│   └── run.py               # CLI 运行脚本
├── data/nlp/
│   ├── intent_train.jsonl   # 客服意图标注数据（25 条种子）
│   └── content_train.jsonl  # 内容审核标注数据（15 条种子）
└── tests/
    └── test_nlp_eval.py     # 评估框架测试（14 tests）
```

## 使用

### 运行评估

```bash
cd ai-services

# 评估规则引擎（rule backend）在意图数据集上的表现
PYTHONPATH=. python -m common.nlp.eval.run intent

# 评估规则引擎在内容审核数据集上的表现
PYTHONPATH=. python -m common.nlp.eval.run content

# 评估 HuggingFace 零样本分类（需安装 transformers）
PYTHONPATH=. python -m common.nlp.eval.run intent --backend huggingface
```

### 输出示例

```
Dataset: intent (25 samples)
Backend: default

RuleBasedIntentClassifier: accuracy=88.00% (22/25) classes=4

label                          precision     recall         f1
----------------------------------------------------------------
category_commission               100.00%     100.00%     100.00%
...
```

## 数据格式

JSONL，每行一条：
```json
{"text": "平台抽成多少？", "label": "category_commission"}
```

### 意图分类标签

| 标签 | 含义 |
|------|------|
| `category_commission` | 佣金/抽成/手续费相关 |
| `category_rating` | 评价/信用/星级相关 |
| `category_refund` | 退款/投诉相关 |
| `category_human` | 转人工/其他 |

### 内容审核标签

| 标签 | 含义 |
|------|------|
| `clean` | 无违规 |
| `spam` | 广告引流 |
| `pii` | 敏感个人信息 |
| `abuse` | 辱骂攻击 |
| `politics` | 涉政敏感 |

## 后续路径

1. **扩充数据**：当前种子数据仅 40 条，生产级微调需 1000+ 条
2. **模型微调**：用标注数据微调 mDeBERTa / BERT-base-chinese
3. **A/B 测试**：rule vs model 并行运行，对比准确率
4. **在线评估**：集成到 CI，PR 变更 NLP 代码时自动跑评估

— *Commons Engine Chief Engineer Bot（AI）*
