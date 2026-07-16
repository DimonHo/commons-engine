#!/usr/bin/env python3
"""Post PR reviews via GitHub API."""
import json
import subprocess
import sys

REPO = "DimonHo/commons-engine"

def gh_token():
    result = subprocess.run(
        ["bash", "-c", "source /opt/data/home/commons-engine/scripts/gh.sh; gh_token"],
        capture_output=True, text=True
    )
    return result.stdout.strip()

def post_review(pr_num, event, body):
    token = gh_token()
    data = json.dumps({"event": event, "body": body})
    result = subprocess.run(
        ["curl", "-s", "-X", "POST",
         "-H", f"Authorization: token {token}",
         "-H", "Accept: application/vnd.github.v3+json",
         "-H", "Content-Type: application/json",
         "-d", data,
         f"https://api.github.com/repos/{REPO}/pulls/{pr_num}/reviews"],
        capture_output=True, text=True
    )
    resp = json.loads(result.stdout)
    print(f"PR #{pr_num} review posted: id={resp.get('id', '?')} state={resp.get('state', '?')}")
    return resp

review_62 = """## P0 修复审查 (commit 9923ead, 2026-07-16)

上次审查提出的两个 P0 问题已在最新 commit 中修复。确认如下:

### [P0 已修复] 支付完整性 -- settle/refund 不再信任客户端金额

已正确修复:
- settle() 不再接收 SettleRequest, 改为调用 service.findById() 从事件存储加载权威交易
- refund() 只接收 reason 字段, 交易同样从事件存储重建
- 新增 PaymentService.findById(): 从 CHARGE_CREATED 事件恢复完整交易信息
- LedgerEvent.ChargeCreated 扩展 workerId + serviceType 字段, 事件溯源完整性提升
- 新增 Flyway migration V8 (worker_id, service_type 列)
- 测试覆盖了负面场景: settle 不存在的交易返回 404, rate 注入被忽略使用默认 80% 规则

### [P0 已修复] 分账比例覆写已移除 -- 治理绕过消除

- SettleRequest DTO 完全移除 (不再有 workerRate/operationRate/commonsRate)
- PaymentService.settle() 签名改为 rule 默认 SettlementRule.DEFAULT, API 层不传 rule
- 治理参数不再可通过 API 覆写, 符合 #50 要求

### 仍需人类维护者确认 (不阻塞技术评审)

Payment 模块涉及资金流转, Governance 模块涉及投票/提案治理。按项目规则, 这两类模块仍需人类维护者在合并前做最终 sign-off。Bot 只做技术层面评审, 不代替人类决策。

### 剩余可合并后跟进项

- #67 enum 解析一致性 (valueOf 不安全变体统一为 400)
- #68 bean validation (@Valid / @NotNull)
- #65 GovernanceApiTest 中 startVote/castVote 的 500 断言需在 #66 合并后更新为 400
- 无认证问题: MVP 阶段可接受, 阶段2 前需引入 Spring Security

技术评审结论: P0 问题已妥善解决, 代码质量良好, 建议人类维护者做最终确认后合并。

-- Commons Engine Bot (AI)"""

review_66 = """## 更新审查 -- CI 修复确认 (commits 9bb2273 + 4214b1c, 2026-07-16)

上次审查(7/15)提出的建议, 作者通过两个 CI 修复 commit 已处理:

### 上次审查建议处理状态

1. [已处理] 依赖范围: build.gradle.kts 现在使用 implementation("spring-boot-starter-web") 而非 api(), 避免了 platform-core 向所有传递依赖方引入 web starter 的问题。建议 #1 已解决。

2. [保持] NotFoundException 继承 BusinessRuleException: 继承关系仅为复用构造器, 子类优先匹配确保 HTTP 状态码正确(404 而非 422)。语义独立性已通过 @ExceptionHandler 分离处理。可接受。

3. [保持] 兜底 @ExceptionHandler(Exception::class): 当前只有一个 advice 类, 无优先级冲突。合理。

4. [非阻塞] HttpRequestMethodNotSupportedException (405) 和 MissingServletRequestParameterException (400): 当前未处理但不阻塞合并, 可作为后续增强。

### 测试改进确认

- GlobalExceptionHandlerTest 从 MockMvc 改为 RANDOM_PORT + JDK HttpClient, 与项目测试模式一致
- 移除了 ObjectMapper 依赖, 使用正则提取 JSON 字段, 减少了测试依赖
- 4 个测试用例 (400/422/404/500) 均验证了状态码、error 字段和 message 内容
- 500 测试验证了不泄漏 java.lang / RuntimeException / jdbc 等内部信息

### 整体评价

代码质量良好, 异常映射设计清晰, 测试覆盖完整。CI 修复后的依赖范围处理正确。

建议合并。合并后 PR #62 中 GovernanceApiTest 的 500 断言需更新为 400 (已在 #65 跟踪)。

-- Commons Engine Bot (AI)"""

if __name__ == "__main__":
    post_review(62, "COMMENT", review_62)
    post_review(66, "COMMENT", review_66)
