# Commons Engine · AI 服务层

Python 独立微服务，为公地引擎核心业务层（Spring Boot 4.x · Kotlin）提供 AI 能力。
这是混合架构的 Python 侧——与核心业务层通过 HTTP/gRPC 通信，各自独立演进。

> 技术栈详见 [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) 第四节。

## 模块

| 模块 | 职责 | 状态 |
|------|------|------|
| `common/` | 共享内核：统一响应模型、配置、结构化日志 | ✅ |
| `customer-service/` | 智能客服：FAQ 路由，复杂问题转人工 | ⏳ MVP（关键词路由） |
| `content-moderation/` | 内容审核：评价/商家信息违规检测 + PII 保护 | ⏳ MVP（规则引擎） |
| `dispatch-optimizer/` | 调度优化：就近匹配 + 负载均衡的可解释建议 | ⏳ MVP（规则引擎） |

> ⏳ = 阶段 2 MVP 实现（基于规则），后续接入 NLP/运筹优化模型时端点签名不变。

## 架构

```
AI 服务层（Python · 独立部署）
    ├── common/              共享内核（统一响应、配置、日志）
    ├── customer-service/    智能客服     :8001
    ├── content-moderation/  内容审核     :8002
    └── dispatch-optimizer/  调度优化     :8003
        ↕ HTTP / gRPC
核心业务层（Spring Boot 4.x · Kotlin）:8080
```

## 设计原则

1. **算法透明**（ARCHITECTURE.md 1.3）：所有判定（审核、派单）返回可解释的 `reason` 字段。
2. **数据主权**（ARCHITECTURE.md 1.5）：PII 检测默认拦截明文手机号/身份证。
3. **反榨取**（ARCHITECTURE.md 3.5）：调度评分含负载惩罚，避免劳动者过劳。
4. **统一响应契约**：所有端点返回 `ApiResponse` 包装，与 Kotlin 侧消费逻辑对齐。

## 开发

### 安装

```bash
# 推荐用 uv（更快）
uv venv .venv --python 3.13
uv pip install -r requirements-dev.txt

# 或用标准 venv
python -m venv .venv
source .venv/bin/activate
pip install -r requirements-dev.txt
```

### 运行测试

```bash
pytest                    # 全部测试（22 用例）
pytest -v                 # 详细输出
pytest tests/test_customer_service.py  # 单模块
```

### 本地运行单个服务

```bash
# 智能客服
uvicorn customer_service.main:app --port 8001 --reload

# 内容审核
uvicorn content_moderation.main:app --port 8002 --reload

# 调度优化
uvicorn dispatch_optimizer.main:app --port 8003 --reload
```

每个服务启动后访问 `/docs` 查看 OpenAPI 交互文档。

## 契约（与核心业务层对齐）

| 端点 | 方法 | 用途 |
|------|------|------|
| `/health` | GET | 健康检查，返回 `{status: "UP", service: "..."}` |
| `/api/v1/customer-service/chat` | POST | 智能客服对话 |
| `/api/v1/content-moderation/moderate` | POST | 内容审核 |
| `/api/v1/dispatch-optimizer/suggest` | POST | 调度建议 |

所有业务端点返回统一包装：
```json
{"success": true, "data": {...}, "message": null}
```
