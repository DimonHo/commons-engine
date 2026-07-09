# 贡献指南

感谢你考虑为公地引擎贡献！这份指南说明如何参与。

---

## 四类贡献者

公地引擎需要不同类型的贡献，你不需要会写代码也能参与：

### 1. 技术人（工程师 / 设计师 / 产品 / 数据）

- 开发公地引擎核心模块代码
- 设计劳动者 App 和消费者 App 的界面与体验
- 撰写技术文档、RFC 提案
- 做数据分析和算法验证

**如何开始**：浏览 GitHub Issues 中标注了 `good first issue` 的任务，或在 Discussions 里提出你感兴趣的模块。

### 2. 劳动者（骑手 / 司机 / 家政工 / 配送员）

- 反馈真实的痛点和需求
- 参与产品设计评审（你们的意见优先级最高）
- 成为第一批合作社成员

**如何开始**：在 GitHub Discussions 的「劳动者之声」分区发言，或联系社区组织者。

### 3. 研究者（法学 / 经济学 / 社会学 / 公共政策）

- 研究合作社法律结构在中国/各地的可行性
- 分析平台经济的抽成机制和数据
- 设计治理模型的实验和评估方案

**如何开始**：在 `docs/research/` 提交研究笔记或提案。

### 4. 组织者（工会 / 社区工作者 / NGO）

- 协助本地合作社的组建
- 连接劳动者社群
- 协调与地方政府的关系

**如何开始**：通过 GitHub Discussions 或直接联系核心团队。

---

## 技术贡献流程

### 提交代码

1. Fork 本仓库
2. 创建分支：`git checkout -b feature/your-feature`
3. 编写代码，确保：
   - 通过现有测试
   - 为新功能编写测试
   - 遵循现有代码风格
4. 提交 Pull Request，描述清楚改了什么、为什么改
5. 等待维护者 review

### 提交 RFC（技术提案）

较大的架构变更或新功能，请走 RFC 流程：

1. 复制 `docs/rfcs/0000-template.md` 为 `docs/rfcs/00NN-短标题.md`
2. 填写提案内容
3. 提交 PR，标题以 `[RFC]` 开头
4. 社区讨论至少 14 天
5. 相关维护者审批

### 开发环境

**前置要求**：JDK 21+、Docker、Git。

```bash
# 克隆仓库
git clone git@github.com:你的用户名/commons-engine.git
cd commons-engine

# 启动开发数据库（PostgreSQL + PostGIS）
docker-compose up -d

# 运行全部测试（Kotlin 后端）
./gradlew test

# 启动应用（Spring Boot 4.x）
./gradlew :backend:app:bootRun

# 端到端冒烟验证（叫车→匹配→派单→分账→评价）
bash deployments/smoke-test.sh
```

> ✅ 阶段 1 MVP 已就绪（2026-07-04）。以上命令均可运行。
> 若本地未安装 JDK 21，可仅运行 `docker-compose up -d` 启动数据库，依赖 CI 验证构建。

---

## 文档贡献

文档和代码同等重要。文档贡献包括：
- 修正错别字、补充说明
- 翻译（中文 ↔ 英文 ↔ 其他语言）
- 撰写使用指南和最佳实践
- 补充案例研究

直接提交 PR 即可。

---

## 社区参与

- **GitHub Issues**：报告 bug、提出功能请求、追踪任务
- **GitHub Discussions**：战略讨论、治理辩论、社区交流
- **定期社区会议**：（待安排）公开议事

---

## 贡献者协议

所有贡献者须遵守：

1. **行为准则**：阅读 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)，保持尊重和建设性。
2. **DCO（Developer Certificate of Origin）**：提交 commit 时加上 `Signed-off-by: 你的名字 <邮箱>`，确认你有权做出该贡献。
   ```bash
   git commit -s -m "你的提交信息"
   ```
3. **开源协议**：所有贡献按 AGPL-3.0（代码）或 CC BY-SA 4.0（文档）授权。

---

## 我们的承诺

- **所有贡献都被看见**：PR 和 RFC 会在合理时间内得到回复。
- **新贡献者友好**：标注了 `good first issue` 的任务适合入门，维护者会提供额外帮助。
- **无门槛参与**：你不需要是资深工程师才能贡献。文档、翻译、测试、反馈都是有价值的贡献。

---

*每一个贡献都在让一个更公平的平台成为可能。谢谢你的参与。*
