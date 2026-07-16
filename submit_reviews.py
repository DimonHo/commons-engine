#!/usr/bin/env python3
"""Submit PR reviews and issue labels."""
import sys
import json
import urllib.request
import os

REPO = "DimonHo/commons-engine"
TOKEN_FILE = os.environ.get("COMMONS_ENGINE_TOKEN_FILE",
    os.path.expanduser("~/.config/commons-engine/github-token"))


def get_token():
    with open(TOKEN_FILE) as f:
        return f.read().strip()


def api(method, endpoint, data=None):
    token = get_token()
    url = f"https://api.github.com/repos/{REPO}{endpoint}"
    headers = {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github.v3+json",
    }
    body = None
    if data is not None:
        headers["Content-Type"] = "application/json"
        body = json.dumps(data).encode()
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        return {"error": e.code, "message": e.read().decode()[:500]}


# --- Review text for PR #66 ---
review_66 = """感谢提交！统一异常处理方案整体质量很好，结构清晰。以下是审查意见：

优点:
- 异常映射规则合理：400/422/404/500 语义清晰
- ErrorResponse 结构化设计 (error/code/message 三层) 便于客户端处理
- 500 响应不泄漏堆栈，测试验证了不含 java.lang / RuntimeException / jdbc 等信息
- 4 种异常场景测试覆盖完整

建议关注:

1. [依赖范围] build.gradle.kts 将 spring-boot-starter 改为 api("spring-boot-starter-web") 会让 platform-core 所有传递依赖方引入 web starter。如果 platform-core 是基础库，建议将 GlobalExceptionHandler 移至 app 模块，或评估 api vs implementation 的影响。

2. [继承语义] NotFoundException 继承 BusinessRuleException，但 404 与 422 语义不同。虽然子类优先匹配使 HTTP 状态码正确，建议在 KDoc 中注明继承关系仅为复用，语义独立。

3. [兜底处理] @ExceptionHandler(Exception::class) 会捕获所有异常。当前只有一个 advice 类无碍，后续若新增其他 advice 需注意优先级。

4. [非阻塞建议] 可补充 HttpRequestMethodNotSupportedException (-> 405) 和 MissingServletRequestParameterException (-> 400)。

整体质量良好，建议合并前处理第 1 点（依赖范围）。

-- Commons Engine Bot (AI)"""

# --- Review text for PR #62 ---
review_62 = """感谢提交！5 模块 REST API 层补齐将 API 覆盖从 2/8 提升到 8/8，代码与现有 MatchingController/MembershipController 模式一致。以下是审查意见：

优点:
- Controller 风格统一：path prefix /api/v1/{module}、open class + 构造器注入、DTO 同文件
- 30 个 HTTP 集成测试覆盖了完整的端点链路
- PaymentApiTest 验证了反榨取底线（劳动者分账 70% 下限）
- GovernanceApiTest 验证了讨论期约束和章程修改 45 天讨论期
- RatingApiTest 验证了双向评价机制

安全与设计建议（需在阶段2前解决）:

1. [安全-无认证] 所有端点缺少认证/授权。PaymentController.refund、GovernanceController.castVote 等敏感端点可被任意调用。MVP 阶段可接受，但阶段2 接入真实用户前必须增加认证层（建议 Spring Security + JWT）。

2. [安全-交易重建] PaymentController.settle/refund 从请求体重建 Transaction 对象（因为 Transaction 不持久化）。客户端可以伪造交易金额、ID。这意味着任何人可以提交任意金额的结算请求。建议引入交易持久化或基于 charge 返回的加密 token 验证。

3. [输入校验] 所有 DTO 缺少 @Valid / @NotNull 校验。例如 PaymentController.charge 的 amount 若为负数或 null 会直接传入 service。建议增加 bean validation。

4. [DispatchController] savePreferences 用 runCatching 忽略无效的 ServiceType 枚举值（mapNotNull + runCatching）。这会静默丢弃无效输入，建议改为抛出明确的 Bad Request。

5. [GovernanceController] createProposal 对 type 使用 getOrDefault(ProposalType.OTHER) 同样静默处理无效类型。治理提案类型不应被静默降级，建议抛出 400。

测试建议:
- 现有测试均为 happy path，建议补充：无效枚举值、缺失必填字段、非法金额等 negative case
- PaymentController 的 settle 端点测试中，charge 和 settle 使用了相同的金额，建议增加金额不一致的测试（当前实现下会成功，但这可能不是预期行为）

整体架构合理，与项目章程原则（透明、反榨取、劳动者所有制）一致。建议在阶段2 前优先解决第 1、2 点安全问题。

-- Commons Engine Bot (AI)"""

if __name__ == "__main__":
    action = sys.argv[1]
    if action == "review_66":
        result = api("POST", "/pulls/66/reviews",
                      {"event": "COMMENT", "body": review_66})
        print("PR #66 review:", "OK" if "id" in result else result)
    elif action == "review_62":
        result = api("POST", "/pulls/62/reviews",
                      {"event": "COMMENT", "body": review_62})
        print("PR #62 review:", "OK" if "id" in result else result)
    elif action == "label_prs":
        # Label the two PRs
        for pr_num, labels in [("66", ["api"]), ("62", ["api"])]:
            result = api("POST", f"/issues/{pr_num}/labels", {"labels": labels})
            print(f"PR #{pr_num} labels:", "OK" if isinstance(result, list) else result)
