# 合作社管理后台（Admin Console）

> **状态**：🟡 脚手架就绪（React + TypeScript + Vite）

合作社运营管理后台：平台总览、会员管理、匹配引擎策略配置、支付分账审计、纠纷仲裁、治理议事。

技术栈：React 18 · TypeScript 5.7 · Vite 6 · React Router 6

## 快速启动

```bash
# 1. 安装依赖
cd clients/admin-console
npm install

# 2. 启动后端（另一个终端，需要 PostgreSQL 运行）
cd ../..  # 回到仓库根
./gradlew bootRun

# 3. 启动前端开发服务器（自动代理 /api → localhost:8080）
npm run dev
# → http://localhost:5173
```

> 开发模式下，Vite 把所有 `/api/*` 请求代理到后端 `localhost:8080`（见 `vite.config.ts`），无需配置 CORS。

## 生产构建

```bash
npm run build     # 输出到 dist/
npm run preview   # 本地预览生产构建
```

构建产物是纯静态文件（JS ~60KB gzip），可托管在任意 CDN/Nginx/对象存储。

## 页面与后端 API 对应

| 页面 | 后端 API 前缀 | 功能 |
|------|--------------|------|
| 总览 | `/api/v1/platform` | 平台健康、模块状态、运营指标 |
| 会员 | `/api/v1/members` | 成员注册、列表、角色统计 |
| 匹配引擎 | `/api/v1/matching` | 策略切换、自动匹配测试 |
| 支付分账 | `/api/v1/payment` | 收费、分账、账本审计 |
| 纠纷仲裁 | `/api/v1/dispute` | 纠纷列表、初筛、仲裁 |
| 治理议事 | `/api/v1/governance` | 提案、投票、表决统计 |

API 客户端代码：`src/api/client.ts`——端点签名严格对齐后端 Kotlin Controller。

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `VITE_API_BASE` | `""` | 后端 API 基础地址（留空走 vite proxy） |

— Commons Engine Chief Engineer Bot（AI）
