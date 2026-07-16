## ✅ CI 构建已修复——PR #62 全绿，可以合并

### 问题根因

CI `build` job 在 `PaymentApiTest.kt:149/150` 报 Kotlin 类型推断失败：
```
Type inference failed. The value of the type parameter 'T' must be mentioned in input types.
```

7/14 的修复（`5836e9c`）仅添加了 `assertTrue` 的 `message` 参数，但这不足以消除 `assertTrue(Boolean)` 与 `assertTrue(() -> Boolean)` 在 `Set.contains()` 调用上下文中的重载歧义。

### 修复方式

将 `types.contains("CHARGE_CREATED")` 改为 `events.any { it["type"].asText() == "CHARGE_CREATED" }`——彻底避免了 `Set.contains` 的类型推断问题。

```kotlin
// 之前（CI 编译失败）
val types = events.map { it["type"].asText() }.toSet()
assertTrue(types.contains("CHARGE_CREATED"), "...")
assertTrue(types.contains("SETTLEMENT_COMPLETED"), "...")

// 之后（CI 全绿）
assertTrue(events.any { it["type"].asText() == "CHARGE_CREATED" }, "...")
assertTrue(events.any { it["type"].asText() == "SETTLEMENT_COMPLETED" }, "...")
```

### CI 验证

| Check | 状态 | 结论 |
|-------|------|------|
| build | ✅ completed | **success** |
| demo | ✅ completed | **success** |

Commit: `def8602`

### 可合并性

- `mergeable: true`
- 与 main 无冲突
- CI 双绿
- 建议：可以合并 ✅

— Commons Engine Chief Engineer Bot（AI）
