## 概要

实现 #63 — 添加统一异常处理 `@RestControllerAdvice`，解决所有业务校验失败返回 HTTP 500 的问题。

Closes #63

## 变更内容

### 新增文件

| 文件 | 说明 |
|------|------|
| `platform-core/.../exception/BusinessRuleException.kt` | 业务规则异常（→422）+ 资源不存在异常（→404） |
| `platform-core/.../exception/GlobalExceptionHandler.kt` | 全局异常处理器（`@RestControllerAdvice`） |
| `platform-core/.../exception/GlobalExceptionHandlerTest.kt` | 4 个集成测试（400/422/404/500） |

### 异常映射规则

| 异常类型 | HTTP 状态码 | error 字段 | 说明 |
|---------|-----------|-----------|------|
| `IllegalArgumentException` | 400 Bad Request | `BAD_REQUEST` | `require{}` 抛出，请求参数无效 |
| `BusinessRuleException` | 422 Unprocessable Entity | `UNPROCESSABLE_ENTITY` | 业务规则违反（如交易状态不允许分账） |
| `NotFoundException` | 404 Not Found | `NOT_FOUND` | 资源不存在 |
| 其他未捕获异常 | 500 Internal Server Error | `INTERNAL_ERROR` | 不泄漏堆栈 |

### 响应体格式

```json
{
  "error": "UNPROCESSABLE_ENTITY",
  "code": "TRANSACTION_NOT_CHARGED",
  "message": "交易必须为 CHARGED 状态才能分账"
}
```

### 模块变更

- `platform-core/build.gradle.kts`：添加 `spring-boot-starter-web` 依赖（用于 `@RestControllerAdvice`）
- 放置在 `platform-core` 模块的 `com.commonsengine.platform.exception` 包，被 `app` 模块的 `@SpringBootApplication`（扫描 `com.commonsengine.**`）自动发现

## 验收标准对照

- [x] 新增 `GlobalExceptionHandler`（`@RestControllerAdvice`）在 `platform-core` 模块
- [x] `IllegalArgumentException` → HTTP 400，结构化错误 JSON
- [x] 自定义业务异常 `BusinessRuleException` → HTTP 422
- [x] 未捕获异常 → HTTP 500，不泄漏堆栈
- [x] 新增测试验证 400/422/404/500 场景（4 个测试用例）
- [x] 现有 `MatchingHttpApiTest` 不受影响（异常处理器只改变错误响应格式）

## 后续

PR #62 中的 `GovernanceApiTest` 有两个测试用例断言 500（`startVote` 和 `castVote` 在讨论期约束下）。本 PR 合并后，这些场景将返回 400（`IllegalArgumentException` → 400），需要在 PR #62 合并后更新测试期望。

## 测试

```bash
./gradlew :backend:platform-core:test
```

4 个测试用例：
1. `IllegalArgumentException returns 400 with BAD_REQUEST error code`
2. `BusinessRuleException returns 422 with UNPROCESSABLE_ENTITY error code`
3. `NotFoundException returns 404 with resource info`
4. `RuntimeException returns 500 without stack trace`

— Commons Engine Chief Engineer Bot（AI）
