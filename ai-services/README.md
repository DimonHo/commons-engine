# Commons Engine · AI 服务层

Python 独立微服务，为公地引擎核心业务层提供 AI 能力。

## 模块

| 模块 | 职责 |
|------|------|
| `customer-service/` | 智能客服：自动处理常见咨询，复杂问题转人工 |
| `content-moderation/` | 内容审核：审核商家信息、用户评价中的违规内容 |
| `dispatch-optimizer/` | 调度优化：基于实时数据的路径和供需优化建议 |

## 架构

```
AI 服务层（Python）
    ↕ HTTP / gRPC
核心业务层（Spring Boot 4.x · Kotlin）
```

## 运行

```bash
pip install -r requirements.txt
python -m customer_service.main
```
