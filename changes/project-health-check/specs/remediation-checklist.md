# Spec: 项目健康检查整改清单

> 变更：`project-health-check`
> 严重度图例：🔴 阻塞（阶段2 启动前必须修复） · 🟡 重要（阶段2 早期修复） · 🟢 改进（随时可做）

---

## 一、文档与代码矛盾

### DOC-1 🔴 CONTRIBUTING.md 完全过时

**位置**: `CONTRIBUTING.md` 第 80–83 行

**问题**:
- 写 `pytest  # 后端`——实际后端是 Kotlin/Gradle，用 `./gradlew test`
- 写「MVP 代码尚未就绪。仓库当前处于阶段 0：文档与章程阶段」——实际阶段1 已完成
- 写「（阶段 1 MVP 代码就绪后）启动开发环境」——应改为现有快速启动指引
- RFC 流程写「复制 `docs/rfcs/0000-template.md`（待创建）」——模板已存在

**整改**: 重写开发环境章节，对齐 README.md 的快速启动（docker-compose + gradlew bootRun + smoke-test.sh）；修正测试命令为 `./gradlew test`；删除阶段0 描述；更新 RFC 模板状态。

---

### DOC-2 🟡 ARCHITECTURE.md 声明了 3 项未实现的核心技术选型

**位置**: `docs/ARCHITECTURE.md` §2 系统架构图、§4.2 技术栈表

**问题**:
| 声明 | 现实 |
|------|------|
| API 网关层（认证 · 限流 · 路由 · 审计日志） | ❌ 无 Spring Security、无认证、无限流、无审计日志 |
| 消息队列 NATS / Redis Streams | ❌ 代码中零引用，事件驱动未实现 |
| GraalVM 原生镜像 | ❌ build.gradle.kts 和 Dockerfile 中无 GraalVM 配置 |

**整改**: 在 ARCHITECTURE.md 每项后标注实现状态（✅ 已实现 / ⏳ 计划中 / 📋 未启动），或将未实现项移至「技术路线图」章节并明确目标阶段。**不应让文档暗示这些已存在。**

---

### DOC-3 🟡 README.md 路线图标记不一致（已合并解决）

**位置**: `README.md` 路线图章节

**问题**: 合并 `feat/payment-persistence-and-demo` 分支时，路线图阶段1 状态标记存在 `[x]` vs `[~]` 冲突，已通过 ours 策略解决（保留 `[x]`）。但需确认合并后内容完整。

**整改**: 验证路线图标记一致性，确认阶段1 完成声明完整。

---

### DOC-4 🟢 RFC-001 状态为「草案」但代码已实现部分参数

**位置**: `docs/rfcs/0001-anti-exploitation-params.md` 状态字段 vs `MatchingStrategy.kt:31-38`

**问题**:
- RFC-001 标注「状态: 草案」，创建于 2026-07-01
- `AntiExploitationConfig` 已在代码中定义 3 个参数（maxMatchRadiusMeters、maxActiveOrders、newcomerProtectionDays）
- RFC 定义的定价约束（maxPlatformFeeRate、minWorkerPayPerOrder）和评价约束（ratingNotLinkedToDispatch）在代码中完全不存在
- newcomerProtectionDays 有 TODO 注释「需配合 Worker 注册时间字段」——即该参数定义了但未生效

**整改**: 将 RFC-001 状态更新为「部分实现」，列出已实现/未实现参数清单；或走正式流程推进至「已通过」并补齐缺失参数。

---

## 二、空壳与未实现

### SHELL-1 🔴 四个核心业务模块未落库（仅内存）

**位置**: `backend/rating`, `backend/dispute`, `backend/dispatch`, `backend/governance`

**问题**:
| 模块 | 源码文件 | 持久化 |
|------|---------|--------|
| rating | Model.kt + RatingService.kt | ❌ 无 Entity / Repository / Migration |
| dispute | Model.kt + DisputeService.kt | ❌ 无 Entity / Repository / Migration |
| dispatch | Model.kt + DispatchService.kt | ❌ 无 Entity / Repository / Migration |
| governance | Model.kt + GovernanceService.kt | ❌ 无 Entity / Repository / Migration |

对比：identity、matching-engine、payment 已有完整的 JPA Entity + Repository + Flyway Migration。

**影响**: demo 使用 H2 内存库重启即丢数据。rating/dispute/governance 的数据在真实 PostgreSQL 下也不会持久化。

**整改**: 这不是健康检查要修的——标注为**阶段2 前置技术债**，在整改清单中列为最高优先技术债。本变更只负责识别和排序。

---

### SHELL-2 🟡 AI 服务层：3 个模块仅 1 个有骨架

**位置**: `ai-services/`

**问题**:
| 模块 | 现状 |
|------|------|
| `customer_service/main.py` | ⚠️ FastAPI 骨架（18 行），health + chat 端点返回「开发中」 |
| `content-moderation/` | ❌ ARCHITECTURE.md 列出，但目录不存在 |
| `dispatch-optimizer/` | ❌ ARCHITECTURE.md 列出，但目录不存在 |

**整改**: 在 ARCHITECTURE.md §3.8 AI 服务层标注实现状态；README 中移除或标注「计划中」。

---

### SHELL-3 🟢 客户端层：三个 App 目录仅有 README 占位

**位置**: `clients/worker-app/`, `clients/consumer-app/`, `clients/admin-console/`

**问题**: 三个目录各只有一个 README.md 占位文件，无任何代码。ARCHITECTURE.md §4.2 列为 React Native / React 技术栈。

**整改**: 在 README 路线图中标注客户端层为阶段2/3 产物；ARCHITECTURE.md §7.1 仓库结构中标注「未启动」。

---

### SHELL-4 🟢 根目录散落临时脚本

**位置**: 仓库根目录、`scripts/`

**问题**:
- 根目录有 `comment_47.py` 等一次性脚本
- `scripts/` 下有 8 个 `_*.py` 脚本（`_check_status.py`、`_create_issues.py`、`_label.py` 等），AGENT_TEAM.md 只提到 `gh.sh`

**整改**: 将临时脚本归档到 `scripts/archive/` 或删除；保留的脚本添加用途注释；更新 AGENT_TEAM.md 工具链章节。

---

## 三、技术债

### DEBT-1 🔴 #44 PostGIS 空间索引未真正使用

**位置**: `backend/matching-engine/`, Issue #44

**问题**:
- ARCHITECTURE.md §3.1 声称「基于地理空间索引（PostGIS / Redis GEO）」
- README 路线图声称持久化层已含 PostGIS
- 实际：worker_locations 表用 `lat/lng DOUBLE PRECISION` 平面列 + 应用层 Haversine 距离计算 + bounding-box 矩形过滤
- 未使用 PostGIS 的 `ST_DWithin`、`geography` 类型或 GiST 索引
- V1 migration 注释明确说明曾删除 `CREATE EXTENSION postgis`

**依赖**: 阻塞高并发真实匹配场景，但在阶段2 冷启动期的低单量下可暂时容忍。

**整改**: 标注为阶段2 早期技术债（日均单量超 ~500 时需处理）。不在本变更中修复。

---

### DEBT-2 🟡 #46 detekt 静态分析 + 测试覆盖率未恢复

**位置**: `build.gradle.kts`（工作区未提交改动）、`config/detekt/detekt.yml`、Issue #46

**问题**:
- detekt 1.23.8 的 Gradle 插件与 KGP 2.3.0（Kotlin 2.3.x）不兼容（字节码硬引用已移除的 `KotlinJvmProjectExtension#getTarget()`）
- 工作区已有替代方案代码（detekt CLI + 自定义 Gradle task），但**未提交**
- 无测试覆盖率报告机制（JaCoCo 已在工作区配置但未提交）

**整改**: 将工作区已有的 detekt CLI + JaCoCo 配置提交、验证通过、关闭 Issue #46。**这是本变更可直接产出的修复之一。**

---

### DEBT-3 🟡 AntiExploitationConfig 参数不完整

**位置**: `backend/matching-engine/src/main/kotlin/.../MatchingStrategy.kt:31-38`

**问题**: RFC-001 定义了 3 类 10 个参数，代码只实现了 3 个（匹配类），缺失：
- ⏳ `minBreakBetweenOrdersMin`（最小休息时间）
- ⏳ `maxDailyActiveHours`（日最大时长）
- ❌ 定价约束 3 个参数（应在 payment 模块）
- ❌ 评价约束 2 个参数（应在 rating 模块）
- ❌ 不可篡改底线校验逻辑（transparentFeeBreakdown、ratingNotLinkedToDispatch 的不可关闭校验）
- newcomerProtectionDays 有 TODO，功能未生效

**整改**: 标注为阶段2 前置——反榨取护栏是公地引擎的核心差异化，必须在试点前完整。

---

### DEBT-4 🟢 缺少部署文档

**位置**: `README.md` 快速启动章节（仅 5 行）

**问题**: README 快速启动只有 docker-compose + bootRun + smoke-test，缺少：
- 生产部署指引（环境变量、数据库配置、Redis 配置）
- AI 服务层部署方式
- CI/CD 流程说明（GitHub Actions workflow 已有但无文档）

**整改**: 在 docs/ 下补充部署文档，或在 README 中展开。

---

## 四、流程缺口

### PROC-1 🟡 代码直接提交 main，PR 审查流程空转

**问题**:
- 审核团队 Agent（AGENT_TEAM.md）设计为审查 PR，但**开放 PR 数量为 0**
- 代码通过直接提交 main 或合并 feature 分支（无 PR 审查环节）
- 刚完成的分支合并（`feat/payment-persistence-and-demo`、`feat/persistence-infra`）就是直接 merge 到 main，没有走 PR review

**整改**: 在整改清单中建议：阶段2 起所有变更走 PR 流程（即使单人维护者也走自审 + CI 验证）。这为社区贡献者建立范例。

---

### PROC-2 🟢 分支保护未启用

**问题**: main 分支未配置保护规则——任何人可直接 push。日报反复标注（B4）。

**整改**: GitHub Settings → Branches → require status checks pass before merge（至少 CI 绿灯）。

---

### PROC-3 🟢 DCO（Signed-off-by）未在 CI 强制

**问题**: CONTRIBUTING.md 未要求 DCO，CI 未检查。对开源项目的法律清晰度有影响。

**整改**: 低优先——等项目有外部贡献者时再引入。

---

## 五、AI Agent 团队校准

### AGENT-1 🟡 Agent 团队已触达规划层边界

**问题**:
- 总工程师 Agent 持续产出规划草案（选址/经济/冷启动），但自 7/5 起 3 份草案均处「待人类评审」状态
- 日报反复标注「Agent 已全面触达边界」「核心小组仍为单人」
- 规划层的边际收益递减——继续产出新文档不会推进项目

**整改**: 
1. 暂停总工程师 Agent 的规划草案产出（cron job 调整）
2. 重新定义 Agent 角色：从「规划产出」转向「工程辅助」——代码审查、文档校验、CI 监控
3. 技术 Agent 日报增加技术债追踪维度

---

### AGENT-2 🟢 Agent 无法本地构建

**问题**: 日报 B3 反复标注「本地环境无 JDK」，Agent 只能依赖 CI 验证，限制了代码审查深度。

**整改**: 评估在 Hermes 环境安装 JDK 21 + Gradle 的可行性，让 Agent 能执行本地 `./gradlew test`。

---

## 整改优先级总结

### 🔴 阻塞（阶段2 启动前必须处理）
| # | 项目 | 类型 |
|---|------|------|
| DOC-1 | CONTRIBUTING.md 重写 | 文档修正 |
| SHELL-1 | 4 模块未落库 | 技术债（标注+排序，不在本变更修） |

### 🟡 重要（阶段2 早期处理）
| # | 项目 | 类型 |
|---|------|------|
| DOC-2 | ARCHITECTURE.md 标注实现状态 | 文档修正 |
| DOC-4 | RFC-001 状态更新 | 文档修正 |
| SHELL-2 | AI 服务层标注实现状态 | 文档修正 |
| DEBT-2 | detekt + JaCoCo 提交验证 | 直接修复 |
| DEBT-3 | AntiExploitationConfig 补全 | 技术债（标注） |
| PROC-1 | PR 流程启用 | 流程建议 |
| AGENT-1 | Agent 角色重新校准 | 运营调整 |

### 🟢 改进（随时可做）
| # | 项目 | 类型 |
|---|------|------|
| DOC-3 | 路线图标记验证 | 文档修正 |
| SHELL-3 | 客户端层标注 | 文档修正 |
| SHELL-4 | 临时脚本清理 | 仓库整理 |
| DEBT-1 | PostGIS（阶段2 早期） | 技术债（标注） |
| DEBT-4 | 部署文档 | 文档补充 |
| PROC-2 | 分支保护 | 流程建议 |
| PROC-3 | DCO 检查 | 流程建议 |
| AGENT-2 | JDK 安装评估 | 环境改进 |
