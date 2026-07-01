# 公地引擎 Agent 团队架构

*围绕 commons-engine 仓库的自动化协作团队——审核、技术、运营三组 AI Agent，每天定时值守。*

---

## 一、设计目标

公地引擎是一个开源项目，需要持续的社区运营：审代码、回 issue、跟踪进度、答疑。在项目早期人手有限时，由 AI Agent 团队承担重复性、值守性工作，让人类贡献者专注于创造性劳动。

三个 Agent 团队各司其职：

```
                    commons-engine 仓库
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐   ┌──────────┐   ┌──────────┐
    │ 审核团队  │   │ 技术团队  │   │ 运营团队  │
    │ Review   │   │ Tech     │   │ Ops      │
    └────┬─────┘   └────┬─────┘   └────┬─────┘
         │              │              │
    代码审查        进度跟踪       社区答疑
    Issue 分诊      阻塞识别       用户咨询
    质量把关        趋势报告       贡献者引导
```

---

## 二、审核团队（Review Agent）

### 职责

| 任务 | 说明 |
|------|------|
| PR 代码审查 | 新 PR 自动审查：代码质量、安全风险、风格一致性、测试覆盖 |
| Issue 分诊 | 新 Issue 自动打标签（bug/feature/question）、评定优先级、分配模块 |
| 合并预检 | 检查 PR 是否通过 CI、是否有冲突、是否需要人工审批 |

### 触发频率

每天 2 次（早 9 点、晚 9 点），扫描过去 12 小时的新 PR 和 Issue。

### 工作流程

```
1. 拉取仓库最新状态（git fetch）
2. 列出所有 open PR + 近 12h 新 Issue
3. 对每个 PR：
   - 读取 diff
   - 审查代码质量、安全、风格
   - 在 PR 下发 review 评论（approve / request changes / comment）
4. 对每个新 Issue：
   - 分析内容，打标签（bug/enhancement/question/discussion）
   - 评定优先级（P0 紧急 / P1 高 / P2 中 / P3 低）
   - 回复初步确认（"已收到，正在分析…"）
5. 汇总当日审核报告
```

---

## 三、技术团队（Tech Agent）

### 职责

| 任务 | 说明 |
|------|------|
| 进度跟踪 | 对照路线图（阶段 0/1/2/3），跟踪各模块开发进度 |
| 阻塞识别 | 标记停滞的 Issue/PR（超 7 天无活动） |
| CI 监控 | 检查最近的 CI 运行状态，报告失败 |
| 每日报告 | 生成开发进度日报（完成项、进行中、阻塞项） |

### 触发频率

每天 1 次（晚 10 点），生成当日进度报告。

### 工作流程

```
1. 读取路线图和 milestone
2. 统计：
   - open/closed issue 数
   - open/merged PR 数
   - 近 7 天 commit 数和贡献者数
   - CI 最近运行状态
3. 识别阻塞项（stale issue/PR）
4. 对照路线图，评估当前阶段完成度
5. 生成每日进度报告，发到指定频道
```

---

## 四、运营团队（Ops Agent）

### 职责

| 任务 | 说明 |
|------|------|
| 答疑 | 回复 Issue 和 Discussions 中的用户提问 |
| 贡献者引导 | 新贡献者的首个 PR/Issue 给予欢迎和引导 |
| FAQ 维护 | 收集高频问题，更新 FAQ 文档 |
| 社区氛围 | 检查是否有违反行为准则的内容 |

### 触发频率

每天 2 次（早 10 点、晚 8 点），扫描未回复的 Issue 和 Discussion。

### 工作流程

```
1. 列出所有无回复的 Issue（@commons-engine-bot 未参与过的）
2. 对每个待回复项：
   - 理解用户问题
   - 查阅项目文档（README/MANIFESTO/ARCHITECTURE/GOVERNANCE）
   - 起草回复（技术问题给方案，概念问题引文档）
   - 发布回复
3. 新贡献者首个 Issue/PR：发欢迎信息 + 贡献指南链接
4. 检查违规内容，报告给维护者
```

### 回复原则

- **诚实**：不确定的不编造，标注"需要维护者确认"
- **引导**：指向文档和贡献指南，而非直接代劳
- **温度**：对新贡献者友好欢迎
- **边界**：只回答与项目相关的问题，不涉及政治/商业争议

---

## 五、技术实现

### 5.1 凭证管理

GitHub API 需要有效 token。token 存储在：

```
~/.config/commons-engine/github-token
```

（不在仓库内，不提交 git）

辅助脚本 `scripts/gh.sh` 封装所有 GitHub API 调用，自动读取 token：

```bash
# 读取 token
TOKEN=$(cat ~/.config/commons-engine/github-token)

# 调用 API
curl -s -H "Authorization: token $TOKEN" \
  "https://api.github.com/repos/DimonHo/commons-engine/issues"
```

### 5.2 仓库信息

- 仓库：`DimonHo/commons-engine`
- 本地路径：`/opt/data/home/commons-engine`
- SSH 远程：`git@github.com:DimonHo/commons-engine.git`（git 操作用）
- API 端点：`https://api.github.com/repos/DimonHo/commons-engine`

### 5.3 调度

三个 Agent 通过 Hermes 的 cron job 调度：

| Agent | 调度 | 说明 |
|-------|------|------|
| 审核团队 | 每天 9:00、21:00 | 早晚各一次审 PR + 分诊 Issue |
| 技术团队 | 每天 22:00 | 晚间生成进度日报 |
| 运营团队 | 每天 10:00、20:00 | 早晚各一次回复社区 |

### 5.4 工具集

每个 Agent 启用：`terminal`（执行脚本/git）、`file`（读写文件）、`web`（查阅资料）。

### 5.5 安全边界

- Agent **不修改**章程（CHARTER.md）、宣言（MANIFESTO.md）等治理根基文档
- Agent **不直接合并** PR（只发 review 评论，合并由人类维护者决定）
- Agent 回复 Issue 时标注自己是 AI（`— Commons Engine Bot`）
- 涉及法律、财务、治理的重大问题，Agent 只转发给维护者，不自行回答

---

## 六、激活步骤

1. 创建 GitHub Personal Access Token（scope: `repo`），存到 `~/.config/commons-engine/github-token`
2. 创建三个 cron job（见 `scripts/` 中的调度配置）
3. 首次手动运行验证，确认 Agent 能正确读写 Issue/PR
4. 进入日常值守

---

*Agent 团队是公地引擎的数字劳动力——它们 7×24 值守，让人类贡献者专注于创造。*
