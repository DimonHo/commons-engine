PR #62 更新审查 -- 聚焦新增测试文件 (2026-07-14 更新)

上次审查 (2026-07-13) 已覆盖 5 个 Controller 的代码质量，提出 2 个 [P0] 和 2 个 [P1] 问题。本次审查聚焦 PR 更新后新增的 5 个 HTTP 集成测试文件 (共 30 个用例)，以及此前 P0 问题的修复状态。

## P0 问题仍未修复

### [P0] 支付完整性 -- settle/refund 仍信任客户端传入的金额

PaymentApiTest `settle with custom rule respects anti-exploitation floor` 测试通过传入 `workerRate=0.70` 验证自定义分账规则。但这恰恰证实了上次审查指出的 P0 问题：**任何 API 调用方仍可自行指定分账比例**。测试将此行为作为期望结果固化，反而增加了修复难度。

settle 端点仍从请求体重建 Transaction (amount、workerId 均由调用方控制)，未从事件存储加载权威记录。建议在修复 service 层后更新此测试。

### [P0] 分账比例覆写仍可绕过治理

`SettleRequest` 仍保留 `workerRate/operationRate/commonsRate` 字段，任何调用方可覆盖分账比例。#50 明确规定分账比例须经全体大会决定。测试 `settle with custom rule` 将这一治理绕过行为作为正常路径验证。

**Payment 和 Governance 模块仍需人类维护者审查后方可合并。**

## 测试质量评估

### 做得好的方面

1. **测试模式一致**：5 个测试文件均采用 `@SpringBootTest(RANDOM_PORT)` + JDK `HttpClient`，与现有 `MatchingHttpApiTest` 模式对齐，走完整 HTTP 路径。
2. **关键业务场景覆盖**：
   - 反榨取底线 (workerRate=0.70 下限验证)
   - 双向评价 (一笔交易两个方向)
   - 讨论期约束 (startVote/castVote 在 DISCUSSION 阶段失败)
   - 章程修改 45 天讨论期验证
3. **链路测试完整**：每个 Controller 的端点均被覆盖，且测试间有合理的链路设计 (先 file 再 screening 再 arbitrate)。
4. **断言信息友好**：assertEquals 带有中文描述信息，便于失败时定位。

### 需关注的问题

1. **测试隔离风险**：所有测试类共享同一个 H2 内存数据库 (`@ActiveProfiles("test")`)，测试间存在隐式数据依赖。`find all returns list` 类测试依赖其他测试创建的数据，如果测试执行顺序变化或并行执行可能失败。建议每个测试方法使用唯一 ID (当前大部分已做到) 或在 `@BeforeEach` 中清理状态。

2. **500 状态码被固化为期望行为**：GovernanceApiTest 中 `start vote before discussion deadline` 和 `cast vote on non-voting proposal` 断言期望 500。虽然注释说明这是当前行为 (无 `@RestControllerAdvice`)，但 #63 实现后这些测试需要同步更新为期望 400/422。建议在测试中添加 TODO 注释标记，避免遗忘。

3. **缺少负面测试**：未测试无效枚举值输入 (如 `serviceType="INVALID"`)、缺少必填字段、空请求体等场景。这些正是上次审查 [P1] 指出的 enum 解析不一致问题最可能暴露的地方。

4. **PaymentApiTest.refund 未验证退款后状态**：测试仅断言 `success=true`，未验证退款后交易状态变更或 history 中是否出现 `REFUND_ISSUED` 事件。

5. **DispatchApiTest.find non-existent preferences 测试**：断言 `200 + null/empty body`。这固化了上次审查 [P2] 指出的「查不到返回 200 而非 404」的设计决策。如果后续统一改为 404，此测试需更新。

## 总结

| 维度 | 评价 |
|------|------|
| 测试覆盖 | 30 个用例覆盖所有端点，正向路径充分 |
| 测试质量 | 模式一致，断言清晰，链路设计合理 |
| 负面测试 | 不足 -- 缺少无效输入、边界条件测试 |
| P0 修复 | 未修复 -- 支付完整性和治理绕过问题仍在 |
| 阻塞合并 | 仍需人类维护者确认 P0 修复后方可合并 |

## 建议

1. **合并前必须**：修复 P0 支付完整性问题 (settle/refund 从事件存储加载交易，移除客户端传入金额)
2. **合并前必须**：移除或限制分账比例覆写 (治理参数由服务层从配置/治理模块解析)
3. **可合并后跟进**：补充负面测试 (#63 实现后同步更新 500->400 的测试期望)
4. **可合并后跟进**：测试隔离改进

-- Commons Engine Bot (AI)