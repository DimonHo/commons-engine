#!/usr/bin/env python3
"""开启阶段1基础设施 PR。"""
import json
import subprocess

BODY = """## 这是什么

把公地引擎**阶段1从"文档阶段"推进到"可运行代码阶段"**。这是本日（项目 Day 0）总工程师直接动手交付的工程基础设施 + 匹配引擎 PoC。

> — Commons Engine Chief Engineer Bot

## 包含内容

**工程基础设施（infra）**
- monorepo 目录骨架：`packages/ clients/ deployments/`
- GitHub Actions CI：`ruff check` + `pytest`，矩阵 Python 3.11 / 3.13
- Issue 模板（bug / feature，含模块+优先级选择）、PR 模板、分支保护基础
- FastAPI 应用骨架（`GET /healthz`）+ `deployments/docker-compose.yml`（PostGIS + Redis）
- pre-commit + ruff 配置
- RFC 模板 `docs/rfcs/0000-template.md`（CONTRIBUTING.md 此前引用但文件缺失）

**匹配引擎 PoC（matching，阶段1首个最小可验证模块）**
纯 stdlib，零第三方依赖，已用 `python3` 冒烟测试通过：
- 领域模型 `Order / Worker / Match`
- Haversine 地理距离
- **3 种可配置策略**：距离优先 / 公平轮转 / 新人保护（引擎不硬编码策略）
- **反榨取约束**：内置最大匹配半径，拒绝系统性压低工资的超长空驶派单
- **可解释理由**：每次匹配产出"为什么把这个单派给你"，劳动者可查、可审计
- 单元测试覆盖核心行为

## 验证状态

| 项 | 状态 |
|----|------|
| 匹配引擎冒烟测试（纯 python3） | ✅ 通过（4 项行为含反榨取过滤） |
| 项目代码行长 / lint（手动） | ✅ 干净（`app/ packages/ tests/` 无违规） |
| 本地全量 pytest（含 FastAPI /healthz） | ⚠️ 本环境 PyPI 网络受限，未能安装 fastapi/httpx；将在 GitHub CI runner 验证 |
| 修改 CHARTER.md / MANIFESTO.md | ✅ 未触碰（治理根基不动） |

## 关联 Issue

Closes #7
Closes #8
Closes #9
Closes #10
Closes #11
Closes #12

推进 matching Epic #2 的子任务 #13 #14 #15 #16 #18（部分落地）。

## 后续

- 待人类维护者开启分支保护（要求 PR + CI 通过 + ≥1 review）后，本仓库即可按规范协作。
- 匹配模块的 PostGIS / Redis GEO 实时检索（#14）与反榨取参数 RFC（#17）为下一步。
"""

payload = json.dumps({
    "title": "阶段1工程基础设施 + 匹配引擎 PoC 脚手架",
    "head": "feat/phase1-infra-matching-poc",
    "base": "main",
    "body": BODY,
    "labels": ["infra", "matching", "P0"],
})

r = subprocess.run(
    ["bash", "-c", f"source scripts/gh.sh; gh_api POST \"/pulls\" '{payload}'"],
    capture_output=True, text=True,
)
try:
    data = json.loads(r.stdout)
    print("PR #%s  %s" % (data.get("number"), data.get("html_url")))
    print("state:", data.get("state"))
except json.JSONDecodeError:
    print("RAW:", r.stdout[:500])
    print("ERR:", r.stderr[:500])
