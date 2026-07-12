## 评审意见 — PR #57: rating 模块 JPA 持久化

### 总体评价

✅ 质量良好。SHELL-1 技术债 1/4，rating 模块从 ConcurrentHashMap 迁移到 JPA + PostgreSQL。与 #58（dispute）高度一致的实现模式，说明参考了 payment/identity 模块的既有最佳实践。

### 优点

1. **CHECK 约束在 DB 层** — `V4__ratings.sql` 的 `score INT NOT NULL CHECK (score >= 1 AND score <= 5)` 在数据库层强制了业务规则，比仅在应用层校验更可靠
2. **tags 序列化容错** — `parseTags()` 使用 `runCatching { RatingTag.valueOf(name.trim()) }.getOrNull()` 的 `mapNotNull` 模式，遇到未知的标签值不会崩溃，而是静默跳过。这是一种防御性解析，合理
3. **4 个索引覆盖查询路径** — rating_id 唯一、ratee_id（信用画像聚合）、transaction_id（双向评价）、rater_id（发出评价），覆盖了所有 service 层查询
4. **事务标注完整** — 写操作 `@Transactional`，读操作 `@Transactional(readOnly = true)`
5. **测试新增 4 个用例** — 双向评价查询、rater 过滤、tags 往返、comment 持久化

### 需关注

1. **tags 分号分隔存储** — 与 #58 的 evidenceUrls 相同的问题。`RatingTag` 是枚举，值不含分号，所以当前安全。但 `parseTags` 的 `runCatching` 会静默丢弃无效值——如果将来枚举值被删除（重命名），旧数据中的标签会无声消失。建议考虑日志 warning 或保留原始字符串

2. **`exportProfile()` 无分页** — `findReceived()` 返回全部评价，数据量大时可能 OOM。MVP 阶段可接受，建议后续加 `Pageable`

3. **`getCreditProfile()` 计算在应用层** — 平均分、标签频次聚合在 Kotlin 侧完成。数据量增长后可考虑下推到 SQL（`AVG(score)` + `GROUP BY`），减少数据传输

4. **`.gitignore` 新增 `scripts/_*.py`** — 这解释了为何临时脚本不进版本控制，合理

### 安全

- ✅ 无注入风险（Spring Data 派生查询）
- ✅ `direction` 使用 `@Enumerated(EnumType.STRING)`

### 结论

代码质量高，与 dispute 模块实现风格一致。建议合并（由人类维护者决定）。

— Commons Engine Bot (AI)
