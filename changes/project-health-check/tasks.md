# Tasks: 项目健康检查整改清单

## File Structure

| 文件 | 职责 | 操作 |
|------|------|------|
| `CONTRIBUTING.md` | 开发指南——重写开发环境章节 | Modify |
| `docs/ARCHITECTURE.md` | 技术架构——技术选型标注实现状态 | Modify |
| `docs/rfcs/0001-anti-exploitation-params.md` | RFC-001——更新状态为部分实现 | Modify |
| `README.md` | 路线图——标注客户端层/AI层实现状态 | Modify |
| `build.gradle.kts` | detekt CLI + JaCoCo 配置——提交工作区改动 | Commit |
| `config/detekt/detekt.yml` | detekt 配置——提交工作区改动 | Commit |
| `changes/project-health-check/` | 本变更所有工件 | Create |

## Interfaces

本变更不产生代码接口。唯一的跨变更接口：
- **Produces**: `specs/remediation-checklist.md`（整改清单）→ 下一变更 `phase2-engineering-blueprint` 的输入
- **Consumes**: 无

---

## Batch 1: 文档修正（无依赖，可并行）

### Task 1: 重写 CONTRIBUTING.md 开发环境章节

**Depends on**: 无

1. 读 `CONTRIBUTING.md` 第 77–84 行（开发环境章节）
2. 将 `pytest  # 后端` 替换为 `./gradlew test  # 后端（Kotlin）`
3. 将「（阶段 1 MVP 代码就绪后）启动开发环境」替换为现有 docker-compose 指引
4. 将「MVP 代码尚未就绪。仓库当前处于阶段 0」替换为「阶段 1 MVP 已就绪」
5. 将 RFC 模板「（待创建）」删除（模板已存在于 `docs/rfcs/0000-template.md`）

### Task 2: ARCHITECTURE.md 标注实现状态

**Depends on**: 无

1. §2 系统架构图下方加注释表，逐层标注：API 网关层 ⏳ 计划中、消息队列 📋 未启动
2. §3.8 AI 服务层：customer-service ⏳ 骨架、content-moderation 📋 未启动、dispatch-optimizer 📋 未启动
3. §4.2 技术栈表新增「实现状态」列：GraalVM 📋、消息队列 📋
4. §7.1 仓库结构标注 `clients/` → 📋 未启动、`ai-services/` → ⏳ 骨架

### Task 3: RFC-001 状态更新

**Depends on**: 无

1. 将 `状态: 草案` 改为 `状态: 部分实现（匹配约束已编码，定价/评价约束待补）`
2. §三参数清单每个参数后标注实现状态（✅/⏳/❌）

### Task 4: 临时脚本归档

**Depends on**: 无

1. `mkdir -p scripts/archive`
2. 将根目录 `comment_47.py` 移入 `scripts/archive/`
3. 将 `scripts/_*.py` 评估用途，无用的移入 `scripts/archive/`

---

## Batch 2: detekt + JaCoCo 提交验证（直接修复 DEBT-2）

### Task 5: 提交工作区 detekt CLI + JaCoCo 配置

**Depends on**: 无（工作区已有改动）

1. `cd /opt/data/home/commons-engine && git diff build.gradle.kts` 确认改动内容
2. `git diff config/detekt/detekt.yml` 确认改动内容
3. `git add build.gradle.kts config/detekt/detekt.yml`
4. `git commit -m "chore(infra): detekt CLI + JaCoCo 覆盖率配置（绕过 KGP 2.3.0 不兼容）#46"`
5. `./gradlew detekt` 验证任务可执行
6. 推送并确认 CI 绿灯

---

## Batch 3: 整改清单整合（依赖 Batch 1 + 2）

### Task 6: 验证整改清单完整性

**Depends on**: Task 1–5

1. 对照 `specs/remediation-checklist.md` 逐项检查：文档修改是否落实
2. 确认 🔴 阻塞项已标注为下一变更的输入
3. 确认 DEBT-2 已关闭

### Task 7: 记录 DP-2 审查门

**Depends on**: Task 6

1. 向用户呈现全部工件摘要
2. 获得确认后执行 `spec-superflow.mjs state set dp_2_result`
3. 交给 contract-builder 或直接关闭（本变更主要是文档，可能跳过执行契约）
