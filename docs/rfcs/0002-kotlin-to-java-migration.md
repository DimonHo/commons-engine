# RFC-0002: 后端 Kotlin → Java 迁移

> **状态**：草案（待维护者决策 / 待社区讨论期开启）
> **作者**：Chief Engineer Bot（AI）
> **创建日期**：2026-07-25
> **相关 Issue**：#79（迁移决策请求）、#80（已合并的迁移 PR）、#81（迁移引入的实体-Schema 漂移）
> **影响模块**：全部 9 个后端模块（platform-core / matching-engine / payment / rating / dispute / dispatch / governance / identity / app）

> ⚠️ **本 RFC 是事后补写的治理文档。** 迁移分支 `feat/kotlin-to-java-migration` 已在无 RFC、无审查、测试归零的情况下被提交（`a78c460`）到 main 分支。按 `docs/GOVERNANCE.md §3.2`，重大技术决策须经 RFC + 14 天讨论期 + 维护者投票。本 RFC 的目的是把已经发生的事纳入正式治理流程，并让维护者据此做出**有依据的**决策（维持 Java / 回滚 Kotlin）。

---

## 一、摘要（Summary）

2026-07-23~24，一条分支将公地引擎后端从 **Spring Boot 4.1 + Kotlin（84 个 `.kt`，170 测试全绿）** 全量改写为 **Spring Boot 4.1 + Java 21（99 个 `.java`）**，构建工具从 Gradle Kotlin DSL（`.gradle.kts`）回退为 Groovy DSL（`.gradle`），并移除了 detekt / kotlin-spring / kotlin-jpa / jackson-module-kotlin 等 Kotlin 生态依赖。

该分支在测试 100% 失败的状态下被提交到 main（CI `build` job: 69 tests completed, 69 failed；`demo` job: 启动失败），使 main 分支当前不可运行。本 RFC 记录迁移的技术事实、动机空白与风险，供维护者决策。

---

## 二、动机（Motivation）

**这是本 RFC 无法填写的部分——也是最大的治理问题。**

迁移分支的提交信息（`e45705c` / `31274c0` / `a78c460`）**没有说明任何迁移动机**：为何放弃 ARCHITECTURE.md L204-212 论证的 7 条 Kotlin 优势？迁移解决了什么 Kotlin 在本项目中遇到的具体痛点？

在没有动机陈述的情况下，无法评估「迁移是否值得」。维护者在决策前应要求迁移作者补充动机。

**客观事实层面**，迁移引入了以下已知代价（与动机无关，均为可验证的回归）：

| 维度 | 迁移前（Kotlin main @ `fb91bf1`） | 迁移后（Java main @ `a78c460`） |
|------|-----------------------------------|--------------------------------|
| 测试 | 170 tests，全绿 | 69 tests，**69 failed（0 通过）** |
| CI 状态 | 🟢 build ✅ demo ✅ | 🔴 build ✗ demo ✗ |
| 构建脚本 | `build.gradle.kts`（Kotlin DSL） | `build.gradle`（Groovy DSL） |
| 静态检查 | detekt（`config/detekt/detekt.yml`） | **已移除插件，但配置文件仍残留**（孤儿） |
| 文档一致性 | ARCHITECTURE.md / README.md 描述 Kotlin | **文档仍写 Kotlin，与代码不符** |

---

## 三、详细设计（Detailed Design）

### 3.1 迁移映射（提交信息自述）

| Kotlin 构造 | Java 等价物 | 备注 |
|-------------|------------|------|
| `data class` | `record`（JDK 21） | OK |
| `@JvmInline value class` | `record` + 静态 `random()` | OK |
| `object`（单例） | `final class` + 静态方法 | OK |
| 扩展函数 | 静态 `Mapper` 类 | OK |
| `sealed class` | `sealed interface` | OK |
| `.gradle.kts` | `.gradle`（Groovy） | 丢失类型安全 |
| kotlin-spring / kotlin-jpa / jackson-module-kotlin | 移除 | **风险点**（见 3.3） |

### 3.2 当前 main 分支状态（`a78c460`，已核查）

- 99 个 `.java` 文件，0 个 `.kt` 文件——Kotlin 代码确实被完全替换。
- 9 个模块（platform-core / matching / payment / rating / dispute / dispatch / governance / identity / app）结构完整。
- 领域模型用嵌套 `record` 承载（如 `Model.WorkerId` / `Model.ConsumerId`），含紧凑构造器校验。
- Spring Boot 4.1.0 BOM、JDK 21 toolchain、JUnit 5。

### 3.3 ⚠️ 迁移引入的具体缺陷（已定位，详见 #81）

**A. Spring 上下文无法启动 → 所有 `@SpringBootTest` 测试失败（69/69）**

根因：迁移后部分 Bean 的装配链断裂。表现是 `NoSuchBeanDefinitionException` / `UnsatisfiedDependencyException`。候选根因：
- 移除 `kotlin-spring` 后，Kotlin 类需要 `kotlin-spring` 打开的 `open` 修饰（用于 CGLIB 代理）问题不复存在，但 Java 端可能遗漏了 `@Configuration` / 组件扫描边界。
- `jackson-module-kotlin` 被移除——若 Kotlin 时代的 DTO 依赖该模块的单参构造反序列化，迁移为 record 后需确认 Jackson 能否正确反序列化（record 的构造器绑定在 Jackson 2.18+ / Spring Boot 4 下通常 OK，但需逐一验证）。

**B. 实体与 Flyway 迁移脚本漂移 → `demo` job（默认 profile，`ddl-auto=validate` + Flyway）启动失败**

已核查到的具体漂移（`backend/matching-engine` 的 `WorkerLocationEntity` vs `V2__worker_locations.sql`）：

| 维度 | JPA 实体声明 | SQL 迁移脚本 | 差异 |
|------|-------------|-------------|------|
| 主键 | `id`（`@Id`, `GenerationType.UUID`, `columnDefinition="uuid"`） | 无 `id` 列（主键是 `worker_id`） | 🔴 实体多一列 + 主键列不同 |
| 列 | `updated_at` | `last_seen_at` | 🔴 列名不同 |
| 列 | （无） | `name`, `service_types`, `rating` | 🔴 实体缺 3 列 |

在 `ddl-auto=validate` 下，Hibernate 启动校验会因「实体列在 schema 中不存在」直接抛 `SchemaManagementException`——这正是 `demo` job 失败的根因。**此漂移在 Kotlin 时代是否已存在，需核查 `fb91bf1`；若迁移引入，属新缺陷。**

**C. 孤儿配置**

`config/detekt/detekt.yml` 仍存在，但 detekt 插件已从所有 `build.gradle` 移除。需删除该目录，或替换为 Java 静态检查（Checkstyle / SpotBugs / Error Prone）。

**D. 文档与代码不一致**

- `README.md` L93 仍写「Spring Boot 4.x + Kotlin」
- `docs/ARCHITECTURE.md` L182 / L191 / L202 / L204-212 仍论证 Kotlin 选型

无论最终选 Kotlin 还是 Java，文档必须与技术栈现实一致。

---

## 四、替代方案（Alternatives）

### 方案 A：维持 Kotlin，回滚 `a78c460`（恢复 `fb91bf1`）
- 成本：最低（一条 `git revert` 或强制回退）。
- 收益：即刻恢复 170 测试全绿 + CI 绿；与全部项目文档一致。
- 代价：迁移工作（若其中确有有价值的设计）被丢弃。
- **总工程师备注**：在没有明确迁移动机的前提下，这是成本/风险最低的选项。

### 方案 B：推进 Java，修复 69 个失败测试 + 实体漂移
- 成本：高。需修复 Spring 装配、逐一核对 9 模块实体与 8 个 Flyway 迁移脚本、补回静态检查、同步文档。
- 收益：若维护者判断 Java 更利于长期贡献者招募（降低 Kotlin 学习门槛），则有战略价值。
- 代价：在修复完成前，main 不可作为工作基准，阶段 1 实质归零。
- **前置条件**：迁移作者须先补充动机（§二），否则无法判断收益是否值得成本。

### 方案 C：双轨——保留 Kotlin main，迁移分支作为实验性 Java 轨道
- 在 RFC 讨论期内，迁移分支不进入 main，待讨论 + 投票后再决定。
- 这是「本应在迁移分支被合并前发生」的流程。

---

## 五、权衡与影响（Trade-offs & Impact）

- **对项目节奏**：main 翻红每多一天，所有下游工作（PR #78 审查、#75 NLP 接入、阶段 2 规划）的基准就越不可信。**时间敏感。**
- **对治理信任**：无 RFC、无审查、无测试即合并，破坏了 `docs/GOVERNANCE.md §3.2` 建立的流程信誉。本 RFC + 维护者明确裁决，是修复这一信任的必要动作。
- **对贡献者**：Kotlin→Java 是语言栈反转，会直接影响任何已在学习 Kotlin 贡献路径的人。须公开讨论。
- **不触及**反榨取、隐私、数据主权原则（本 RFC 是工程栈选择，不改变算法/治理语义）。

---

## 六、未决问题（Open Questions）

> 以下需**人类维护者**决定（按职责，Bot 不代决）：

1. **[维护者决策]** 选择方案 A / B / C？这是本 RFC 的核心裁决。
2. **[迁移作者]** 迁移的动机是什么？（§二 无法由总工程师代填）
3. **[维护者]** 若选 B（推进 Java），静态检查采用 Checkstyle / SpotBugs / Error Prone 中的哪个？
4. **[维护者]** 若选 A（回滚），是否保留迁移分支为参考？

---

## 七、总工程师建议（非决策）

作为 AI 总工程师，我的职责是呈现事实与选项，**不预设语言栈结论**——Kotlin 还是 Java 是维护者的决定权。

但无论选哪个方案，以下是不可妥协的工程底线：
1. **「无测试不合并」**——`CONTRIBUTING.md` 已明文。迁移分支当前 69 测试全失败，不满足合并条件。
2. **「重大技术决策经 RFC」**——`docs/GOVERNANCE.md §3.2` 已明文。本 RFC 即为补齐此流程。
3. **main 不可长期翻红**——每多一天，项目健康度实质性下降。

在维护者裁决前，所有 Agent 以 **main 当前 HEAD（`a78c460`，Java）为事实基准进行只读分析**，但**不在此基准上创建新功能分支或合并新 PR**，以免裁决为「回滚」时产生无效工作。

— Commons Engine Chief Engineer Bot（AI）
