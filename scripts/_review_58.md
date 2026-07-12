## 评审意见 — PR #58: dispute 模块 JPA 持久化

### 总体评价

✅ 质量良好。SHELL-1 技术债 2/4，从 ConcurrentHashMap 迁移到 JPA + PostgreSQL，遵循了 payment/identity 模块已有模式，代码结构清晰。

### 优点

1. **Entity↔Domain 映射干净** — `toDomain()` / `toEntity()` 分离了持久层与领域模型，`Dispute.toEntity()` 不设主键，由 DB 管理
2. **状态机字段正确使用 `var`** — `status`、`updatedAt`、`resolution`、`resolvedAt` 设为 mutable，在 managed entity 上直接修改避免了脏拷贝
3. **Flyway 迁移规范** — 4 个索引覆盖了所有高频查询路径（dispute_id 唯一、status 看板、filed_by/filed_against 双向），`GENERATED ALWAYS AS IDENTITY` 符合 PG 最佳实践
4. **事务边界正确** — `@Transactional` 用于写操作，`@Transactional(readOnly = true)` 用于查询
5. **测试新增 3 个用例** — evidenceUrls 往返、resolution 持久化、findAll，覆盖了关键持久化路径

### 需关注

1. **`evidenceUrls` 分号分隔存储** — 如果 URL 本身含 `;` 会造成解析错误。当前 `toDomain()` 用 `split(";")` 无转义机制。MVP 阶段可接受，但建议在 `DisputeEntity` KDoc 中明确标注此限制（已有注释 ✅）。后续数据量增长时可考虑 JSONB 列或关联表

2. **`@Transactional` 测试隔离** — `DisputeServiceTest` 使用 `@Transactional`，测试数据自动回滚。这是 Spring 推荐做法，但意味着测试不验证跨事务边界的真实持久化。当前通过 `findById` 回查验证了持久层往返，对于模块级测试足够。如需更强保证，可考虑 `@DirtiesContext` 或 Testcontainers

3. **`findByDisputeId` 返回 nullable** — service 层用 `findByDisputeIdOrThrow()` 封装了 null 检查，良好。但 `findById()` 直接返回 nullable（`?`），调用方需注意

### 安全

- ✅ 无原始 SQL，全部使用 Spring Data 派生查询，无注入风险
- ✅ 枚举使用 `@Enumerated(EnumType.STRING)`，避免了序号映射的脆弱性

### 结论

代码质量高，模式一致性好，测试覆盖到位。建议合并（由人类维护者决定）。

— Commons Engine Bot (AI)
