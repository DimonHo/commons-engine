#!/usr/bin/env python3
"""
阶段 1 Issue 拆解脚本 — 把公地引擎 Phase 1 (MVP) 拆成可追踪的 Epic + 子任务。
幂等：标签已存在则跳过；Issue 通过标题去重避免重复创建。
"""
import json
import subprocess
import sys

LABELS = {
    # 模块标签
    "matching":   ("0E8A16", "匹配引擎模块"),
    "payment":    ("1D76DB", "支付分账模块"),
    "rating":     ("D93F0B", "信用评价模块"),
    "dispute":    ("BF8700", "纠纷仲裁模块"),
    "governance": ("8957E5", "治理模块"),
    "dispatch":   ("A371F7", "调度引擎模块"),
    "identity":   ("2188FF", "会员身份系统"),
    "infra":      ("57606A", "工程基础设施"),
    "docs":       ("0075ca", "文档"),
    "org":        ("C2E0C6", "组织 / 社区"),
    # 优先级标签
    "P0": ("B60205", "紧急：阻塞一切"),
    "P1": ("D93F0B", "高：本周必须推进"),
    "P2": ("FBCA04", "中：本月推进"),
    "P3": ("0E8A16", "低：可延后"),
    # 类型
    "epic": ("3E4601", "Epic（大特性，拆子任务）"),
}


def gh(endpoint, data=None, method=None):
    fn = f'gh_api {"GET" if data is None else "POST"} "{endpoint}"'
    if data is not None:
        # gh_api expects a JSON string as single arg
        fn = f'gh_api POST "{endpoint}" \'{data}\''
    r = subprocess.run(['bash', '-c', f'source scripts/gh.sh; {fn}'],
                       capture_output=True, text=True)
    if r.returncode != 0:
        return None, r.stderr
    try:
        return json.loads(r.stdout), None
    except json.JSONDecodeError:
        return None, r.stdout


def ensure_labels():
    existing, _ = gh("/labels")
    existing = {l['name'] for l in (existing or [])}
    created = []
    for name, (color, desc) in LABELS.items():
        if name in existing:
            continue
        payload = json.dumps({"name": name, "color": color, "description": desc})
        r, err = gh("/labels", data=payload)
        if r is None and "already_exists" not in str(err):
            print(f"  ! label {name}: {err}")
        else:
            created.append(name)
    print(f"  labels ensured (created {len(created)}): {created}")


def list_existing_titles():
    r, _ = gh("/issues?state=open&per_page=100")
    return {i['title'] for i in (r or []) if 'pull_request' not in i}


def create_issue(title, body, labels):
    payload = json.dumps({"title": title, "body": body, "labels": labels})
    r, err = gh("/issues", data=payload)
    if r is None:
        print(f"  ! FAILED: {title}\n      {err}")
        return None
    print(f"  #{r['number']}  {title}  [{','.join(labels)}]")
    return r['number']


def md_body(*sections):
    return "\n\n".join(sections).strip()


def main():
    print("== 1. 确保标签 ==")
    ensure_labels()

    existing = list_existing_titles()
    if existing:
        print(f"  已存在 open issue: {len(existing)} 个")

    print("\n== 2. 创建 Epic（先建，以便子任务引用编号）==")

    epics = {
        "infra": "[Epic] [infra] 阶段1 工程基础设施搭建（解锁所有代码工作）",
        "matching": "[Epic] [matching] 匹配引擎 MVP（阶段1 首个 PoC 模块）",
        "payment": "[Epic] [payment] 支付分账 MVP",
        "rating": "[Epic] [rating] 信用评价 MVP",
        "dispute": "[Epic] [dispute] 纠纷仲裁 MVP",
        "governance": "[Epic] [governance] 治理模块 MVP（投票 + 透明账目）",
    }

    epic_bodies = {
        "infra": md_body(
            "**目标**：在写第一行业务代码之前，把 monorepo 结构、CI、模板、开发环境、lint 配置全部搭好。这是所有模块工作的前置依赖，优先级最高。",
            "**验收标准**",
            "- 仓库具备 `packages/ clients/ deployments/` 目录骨架",
            "- GitHub Actions CI 在 PR 上自动跑 lint + test，且对当前空仓库能通过",
            "- `.github/` 下有 Issue 模板、PR 模板",
            "- `docker-compose.yml` 可一键拉起 PostgreSQL+PostGIS + Redis 开发依赖",
            "- `docs/rfcs/0000-template.md` 存在（CONTRIBUTING.md 已引用但缺失）",
            "- 分支保护策略有文档说明（建议：PR + CI 通过 + 至少1 review）",
            "**依赖**：无。**优先级：P0。**",
        ),
        "matching": md_body(
            "**目标**：实现公地引擎第一个端到端可运行模块——实时打车场景的供需匹配。匹配模块依赖最少、最能验证整体架构，是阶段1的 PoC 切入点（见架构文档 3.1）。",
            "**核心要求**：算法可配置、决策可解释、内置反榨取约束。",
            "**验收标准（Epic 整体）**",
            "- 一个可在测试环境跑通「乘客叫车 → 匹配到司机 → 返回派单理由」完整链路的 demo",
            "- 匹配规则可由配置切换（距离优先 / 公平轮转 / 新人保护）",
            "- 劳动者可通过 API 查看「为什么把这个单派给我」",
            "- 模块单元测试覆盖率 ≥ 70%",
            "**依赖**：infra Epic（#见编号）。**优先级：P1。**",
        ),
        "payment": md_body(
            "**目标**：透明、可审计、可配置分账的资金收付（见架构 3.2）。MVP 阶段先打通单一支付通道 + 事件溯源账本 + 可配置分账规则。",
            "**验收标准**",
            "- 支付适配器抽象层，至少接 1 个通道（沙箱即可）",
            "- 所有资金流水进事件溯源账本，不可篡改、可审计",
            "- 分账比例可配置且对劳动者/消费者完全可见",
            "**依赖**：infra。**优先级：P2（在匹配 PoC 之后）。**",
        ),
        "rating": md_body(
            "**目标**：双向信用评价，打破单向权力，反惩罚性设计，数据归个人（见架构 3.3）。",
            "**验收标准**",
            "- 劳动者评用户 / 用户评劳动者 双向评价",
            "- 评价数据可携带导出（劳动者离开合作社可带走）",
            "- 评价不直接挂钩接单资格",
            "**依赖**：identity（成员系统）。**优先级：P2。**",
        ),
        "dispute": md_body(
            "**目标**：AI 初筛 + 人工仲裁的纠纷处理流程（见架构 3.4）。透明、可申诉、结果理由可解释。",
            "**验收标准**",
            "- 纠纷工单状态机（提交→AI初筛→仲裁→可申诉）",
            "- AI 初筛产出证据摘要与分类建议",
            "- 仲裁规则公开，结果附理由",
            "**依赖**：rating, identity。**优先级：P3。**",
        ),
        "governance": md_body(
            "**目标**：支撑合作社民主治理——多利益相关方加权投票、议事、按劳动分红、透明账目（见架构 3.6）。",
            "**验收标准**",
            "- 多利益相关方（劳动者/用户/社区）加权投票可运行",
            "- 提案→讨论→表决线上流程",
            "- 平台收支/抽成流向对全体成员公开",
            "**注意**：分红权重、投票权重属治理重大事项，最终参数需人类维护者/全体大会决定，本 Epic 只做技术载体。",
            "**依赖**：identity。**优先级：P3。**",
        ),
    }

    epic_numbers = {}
    for key, title in epics.items():
        if title in existing:
            print(f"  - skip (exists): {title}")
            # 尝试取编号
            r, _ = gh("/issues?state=open&per_page=100")
            for i in (r or []):
                if i['title'] == title:
                    epic_numbers[key] = i['number']
            continue
        num = create_issue(title, epic_bodies[key], ["epic", key,
                          "P0" if key in ("infra", "matching") else "P2"])
        epic_numbers[key] = num

    print("\n== 3. 创建子任务 Issue ==")

    # (module_key, title, labels, priority, body)
    subtasks = [
        # ---- infra 子任务 (P0) ----
        ("infra", "[infra] 建立 monorepo 目录骨架 (packages/ clients/ deployments/)", ["infra"], "P0",
         "**做什么**：按 ARCHITECTURE.md §7.1 创建 `packages/{matching-engine,payment,rating,dispute,dispatch,governance,identity,ai-services}`、`clients/{worker-app,consumer-app,admin-console}`、`deployments/`、`docs/rfcs/` 目录与各自 README 占位。\n\n**验收**：目录结构存在，每个 package 有 `README.md`（写明职责与状态: not-started）与 `.gitkeep`。"),
        ("infra", "[infra] 配置 GitHub Actions CI（lint + test，PR 触发）", ["infra"], "P0",
         "**做什么**：`.github/workflows/ci.yml`，Python 3.13，matrix 在 PR/push 时跑 `ruff check` + `pytest`（空仓库也通过）。\n\n**验收**：PR 上出现绿色 CI 徽标；空提交/骨架提交全绿。\n\n**依赖**：先确定 lint 工具（建议 ruff）。"),
        ("infra", "[infra] 添加 Issue 模板 / PR 模板", ["infra", "docs"], "P0",
         "**做什么**：`.github/ISSUE_TEMPLATE/{bug_report,feature_request}.yml`、`.github/PULL_REQUEST_TEMPLATE.md`（含 checklist：测试、文档、DCO sign-off）。\n\n**验收**：新建 Issue 时出现模板选择；新建 PR 自动带模板。"),
        ("infra", "[infra] 搭建 FastAPI 骨架 + docker-compose 开发依赖", ["infra"], "P1",
         "**做什么**：根级 `pyproject.toml`（uv 管理）、一个最小 FastAPI app（`/healthz`）、`deployments/docker-compose.yml`（PostgreSQL+PostGIS + Redis）。\n\n**验收**：`docker-compose up -d` 起依赖；`uvicorn app:app` 返回 `/healthz` 200；`pytest` 通过。"),
        ("infra", "[infra] 建立 lint/format 与 pre-commit 配置（ruff）", ["infra"], "P1",
         "**做什么**：根 `ruff.toml`/`pyproject` 配置、`.pre-commit-config.yaml`、CONTRIBUTING 补开发命令。\n\n**验收**：`ruff check .` 通过；`pre-commit run --all-files` 通过。"),
        ("infra", "[docs] 创建 RFC 模板 docs/rfcs/0000-template.md", ["infra", "docs"], "P2",
         "**做什么**：CONTRIBUTING.md 已引用 `docs/rfcs/0000-template.md` 但文件缺失。补一个标准 RFC 模板（标题/状态/动机/方案/权衡/影响）。\n\n**验收**：文件存在；CONTRIBUTING 链接可达。"),

        # ---- matching 子任务 (P1) ----
        ("matching", "[matching] 定义领域模型与数据契约 (Order/Worker/Match)", ["matching"], "P1",
         "**做什么**：定义乘客需求(Order)、劳动者供给(Worker)、匹配结果(Match) 的领域模型与 OpenAPI 数据契约。\n\n**验收**：模型有类型注解 + 单测；OpenAPI schema 生成成功。"),
        ("matching", "[matching] 实现基于地理空间的实时匹配核心 (PostGIS/Redis GEO)", ["matching"], "P1",
         "**做什么**：实时供需匹配核心——基于位置的司机/骑手检索（PostGIS 或 Redis GEO），事件驱动派单。\n\n**验收**：给定一组供给与需求，匹配核心能在合理时间内返回候选；有端到端测试。"),
        ("matching", "[matching] 匹配算法可配置化（规则引擎：距离/轮转/新人保护）", ["matching"], "P1",
         "**做什么**：把「派单规则」抽象为可配置策略（距离优先/公平轮转/新人保护），平台按区域配置，引擎不硬编码。\n\n**验收**：切换配置即可改变派单行为；每种策略有测试。"),
        ("matching", "[matching] 匹配决策可解释 API（劳动者可见派单理由）", ["matching"], "P1",
         "**做什么**：每次匹配产出「为什么派给我这个单」的解释（命中规则、距离、评分等），劳动者可查。\n\n**验收**：API 返回结构化解释；可审计日志。"),
        ("matching", "[matching] 反榨取约束参数设计（RFC）", ["matching", "docs"], "P2",
         "**做什么**：写一份 RFC，定义引擎内置的「反系统性压低工资」约束参数（如最低派单密度、最长空驶保护）。架构 3.1 要求。\n\n**验收**：RFC 进入讨论；至少给出参数清单与默认值建议。\n\n**注意**：参数最终值属治理事项，需人类维护者/全体大会决定。"),
        ("matching", "[matching] 匹配模块单元测试 + 端到端 demo", ["matching"], "P1",
         "**做什么**：覆盖率 ≥70% 的单测 + 一个可在测试环境跑通「叫车→匹配→派单理由」的 demo 脚本与说明。\n\n**验收**：CI 中测试通过；README 有 demo 复现步骤。"),

        # ---- payment 子任务 (P2) ----
        ("payment", "[payment] 支付适配器层抽象（adapter pattern，多通道）", ["payment"], "P2",
         "**做什么**：定义统一 PaymentProvider 接口，预留微信/支付宝/银行适配，先实现一个沙箱 mock。\n\n**验收**：接口定义 + mock 实现 + 测试；新增通道不改业务层。"),
        ("payment", "[payment] 事件溯源账本设计（不可篡改资金流水）", ["payment"], "P2",
         "**做什么**：所有资金进出用事件溯源记录，append-only，可重放、可审计。\n\n**验收**：账本可重放出当前余额；篡改检测测试通过。"),
        ("payment", "[payment] 可配置分账规则引擎 + 公开查询 API", ["payment"], "P2",
         "**做什么**：分账比例（劳动者/运营成本/公积金）可配置、交易时对双方可见，全体成员可查账。\n\n**验收**：给定分账规则与金额，正确计算各方份额并落账；有公开查询端点。\n\n**注意**：分账比例属治理事项，引擎只承载参数。"),
        ("payment", "[payment] 单通道收付款 PoC（沙箱端到端）", ["payment"], "P3",
         "**做什么**：用 mock 通道跑通「收款→分账→落账→查询」端到端。\n\n**验收**：PoC 脚本可复现；账目正确。"),

        # ---- rating 子任务 (P2) ----
        ("rating", "[rating] 双向评价数据模型与 API", ["rating"], "P2",
         "**做什么**：劳动者评用户 / 用户评劳动者的双向评价模型 + 评价 API。\n\n**验收**：双向评价均可提交；信用聚合计算正确。"),
        ("rating", "[rating] 评价数据可携带导出（数据归个人）", ["rating"], "P2",
         "**做什么**：劳动者可导出自己的全部评价/信用记录（架构 3.3 要求）。\n\n**验收**：导出端点返回标准格式（JSON/CSV）；测试覆盖。"),
        ("rating", "[rating] 反惩罚性设计规则（评价不挂钩接单资格）", ["rating", "docs"], "P2",
         "**做什么**：明确并实现「评价仅作参考、不直接决定接单资格」的约束 + 文档说明。\n\n**验收**：代码层面评价不进入派单准入判定；有 RFC/文档记录该原则。"),

        # ---- dispute 子任务 (P3) ----
        ("dispute", "[dispute] 纠纷工单数据模型与状态机", ["dispute"], "P3",
         "**做什么**：工单模型 + 状态机（提交→AI初筛→仲裁→可申诉→结案）。\n\n**验收**：状态流转合法路径有测试；非法流转被拒。"),
        ("dispute", "[dispute] AI 初筛分类 pipeline（证据摘要）", ["dispute"], "P3",
         "**做什么**：AI 对纠纷做分类 + 证据整理摘要，供仲裁参考。\n\n**验收**：给定样例纠纷，产出结构化分类与摘要；模型层可替换。"),
        ("dispute", "[dispute] 仲裁流程 + 结果理由透明化", ["dispute"], "P3",
         "**做什么**：仲裁委员会（多利益相关方）角色权限 + 结果必须附理由，劳动者可申诉。\n\n**验收**：仲裁结果含理由字段；申诉路径可用。\n\n**注意**：仲裁委员会组成比例属治理事项（见 GOVERNANCE）。"),

        # ---- governance 子任务 (P3) ----
        ("governance", "[governance] 多利益相关方加权投票系统", ["governance"], "P3",
         "**做什么**：劳动者/用户/社区三方加权投票，权重可配置。\n\n**验收**：发起投票→投票→计票→出结果全流程可运行。\n\n**注意**：权重值属治理事项，需人类维护者/全体大会决定。"),
        ("governance", "[governance] 议事平台（提案/讨论/表决）", ["governance"], "P3",
         "**做什么**：提案→讨论→表决的线上流程载体。\n\n**验收**：可发起提案、讨论、转入表决、记录结果。"),
        ("governance", "[governance] 透明账目公开看板", ["governance"], "P3",
         "**做什么**：平台收支、抽成流向对全体成员公开的只读看板。\n\n**验收**：只读 API 返回脱敏公开账目；权限隔离正确。"),
        ("governance", "[governance] 按劳动贡献分红计算（技术载体）", ["governance"], "P3",
         "**做什么**：按可配置的劳动贡献公式计算利润分配。\n\n**验收**：给定贡献数据与公式，正确计算各方分红。\n\n**注意**：分红公式属治理/财务重大事项，需人类维护者决定，本任务只做可配置技术载体。"),

        # ---- 跨模块依赖：identity (P2) ----
        ("identity", "[identity] 会员与身份系统 MVP（账户/角色/份额记录）", ["identity"], "P2",
         "**做什么**：劳动者/用户/合作社成员的身份、角色、份额/股权记录基础。架构 3.7。\n\n**验收**：注册→身份核验占位→角色赋权；份额记录可查。隐私数据加密存储占位。\n\n**依赖**：infra。rating/payment/governance 均依赖本模块，建议在它们之前启动。"),
    ]

    created = 0
    for mod, title, extra_labels, prio, body in subtasks:
        if title in existing:
            print(f"  - skip (exists): {title}")
            continue
        labels = list(set(extra_labels + [mod, prio]))
        full_body = body
        if mod in epic_numbers and epic_numbers[mod]:
            full_body += f"\n\n**所属 Epic**: #{epic_numbers[mod]}"
        create_issue(title, full_body, labels)
        created += 1

    print(f"\n完成。新建子任务 {created} 个。Epic 编号: {epic_numbers}")


if __name__ == "__main__":
    main()
