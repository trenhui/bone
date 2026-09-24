# ADR-0020：反贫血机制 — 弱约束代码形式、强约束行为

| 项 | 内容 |
|----|------|
| **状态** | 提议 |
| **日期** | 2026-08-30 |
| **决策者** | 平台架构组（待评审） |
| **关联** | [Bone-DDD v5.1.0 E-6](../Bone-DDD-最终实践方案.md#e-6-领域模型与持久化模型)、[门禁分级](../Bone-DDD-最终实践方案.md#g-1-1-hard-gate)；change `ddd-spec-v4-5-convergence`（D12） |

---

> **v5.0 状态说明**：本 ADR 未转为“已接受”。[ADR-0024](./0024-ddd-v5-rule-semantics-and-document-split.md) 保留“弱约束代码形式、重视领域行为”的方向，但将 `AggregatePureUnitTestGuard` 定位为**测试卫生检查**，不再作为反贫血主判据。反贫血由领域行为、拒绝路径测试和语义评审共同证明。

## 背景

反贫血（禁止聚合退化为数据袋）在 Bone 面临一个硬约束：`AggregateRoot.setId` 与 `TenantAggregateRoot.setTenantId` 是 **public**，这是 `bone-metadata-sdk` 反射回填主键与 `Tenantable` 契约所必需，全平台大量依赖，**不可降级为 protected**。因此无法通过「禁止 setter 声明」实现反贫血。

既有方案转向调用点约束（`outerLayersMustNotMutateAggregateIdentity()`），但在 v4.5 收敛过程中出现了**过度工程化**的两个提案：

1. **强制 `reconstitute(...)` 静态工厂**作为 ORM 重建入口；
2. **把调用点规则扩展为「外层禁止调用聚合的所有 `set*`」**。

实测证明二者均不成立（见「理由」）。同时，基类去 `@Data` 被误判为大型工程改造，实际已完成。

## 决策

反贫血治理采用「**弱约束代码形式、强约束行为**」——**代码形态不是可靠判据，「能否在纯单测里让不变量失败」才是**。按杠杆排序落实：

1. **（主判据）P2 聚合纯单测**：每个聚合根须有可在无容器下运行的纯单测，覆盖主状态机与 ≥1 条拒绝路径。
   - **实现（2026-08-30 实现）**：`AggregatePureUnitTestGuard.verify("<根包>")`（`bone-architecture-test`）。设计为**独立守卫类而非 `ArchRule`**——ArchUnit 的 `@AnalyzeClasses` 默认带 `ImportOption.DoNotIncludeTests`，`ArchitectureTest` 看不到测试类，无法判定对应关系；故改用 `ClassFileImporter` 分别导入主代码与测试代码两个类集合交叉比对，由模块内独立测试类承载。
   - **判据三条全满足才算通过**：① 存在同名 `*Test` 类；② **至少 1 个 `@Test` 方法**（已堵住「空测试类绕过」这一最廉价绕过路径）；③ **无容器注解**（`@SpringBootTest` / `@DataJpaTest` / `@ExtendWith(SpringExtension.class)` 等）——比原「低成本强化」建议更严格，直接保证「能在无 Spring / DB / MQ 下运行」。
   - 仅统计 `..domain..` 下的**具体**聚合根；基础设施持久化记录（如 `OrderOutboxRecord`）虽继承 `AggregateRoot`，属技术对象不计入。
   - **剩余局限（诚实定位）**：仍不保证断言质量（空断言、只测 happy path 仍可绕过），它依然是**存在性 + 可执行性**门禁，不是内容门禁；内容质量由 CR 检查清单 + 测试模板保证。
   - **验证**：`bone-blueprint` 接入后 137 测试全绿；负向对照（临时新增无测试的探针聚合）被准确拦截。
2. **（强约束）身份早期确定**：id 作为构造参数传入，而非 setter 后补——依赖 **ADR-0019** 实现后方可成立（当前 `insert()` 会覆盖预置 id）。
3. **（弱约束·基础设施细节）ORM 恢复不要求业务层提供 `reconstitute()`**：SDK 通过字段级反射恢复对象（`SmartRowMapper` 的 `field.setAccessible(true); field.set(...)`；主键回填走 `ReflectionUtil.setFieldValue`），实例经无参构造器创建，**整个过程不经由 setter**。故「重建入口」不构成对业务层的约束。
4. **（去掉）不强制 `reconstitute()`，也不把调用点规则扩展为「禁所有 `set*`」**。
5. **（保留·硬门禁）`outerLayersMustNotMutateAggregateIdentity()` 维持 A 类强制规则**，禁止外层篡改 `setId` / `setTenantId`——守护租户隔离与身份完整性，**不得降级为可选**。
6. **（有界任务）`@Data` 收敛**：与「setter 可见性」拆分为两个独立问题。

## 理由

- **强制 `reconstitute()` 测不到目标**：它约束的是「是否有这个静态方法」，而真正要防的是「外层绕过行为方法改状态」。前者可 100% 满足而后者照犯，且会逼出 `create()` + `reconstitute()` 双入口冗余。
- **扩展为「禁所有 `set*`」不可行**：public setter 是 SDK 契约，调用点禁用会在重建、测试装配等合理场景大量误伤；且该规则同样测不到「是否绕过行为」。
- **ORM 恢复经字段级反射已证实**：`SmartRowMapper.mapRow` 用 `field.set(...)`；`createInstance` 用 `BeanUtils.instantiateClass`（`BeanUtils` 仅用于实例化，非 `copyProperties`）；主键回填用 `ReflectionUtil.setFieldValue`。**全程不经 setter** → 去 `@Data` 不影响持久化。
- **基类去 `@Data` 是已完成项**：实测 `AggregateRoot` / `TenantAggregateRoot` 已仅用 `@Getter` + 手写 `setId` / `setTenantId`，从未使用 `@Data`。
- **与公开著作一致**：Evans / Vernon（IDDD）/ Spring 官方 DDD 指南均强调行为与不变量，而非强制的重建方法命名。

## 后果

### 正面

- 治理聚焦可机器判定的行为证据，避免「ArchUnit 全绿但聚合无行为」的形式合规。
- 消除 `reconstitute()` 双入口冗余与「禁所有 `set*`」的误伤。
- 明确保留身份/租户保护的硬门禁，隔离安全不弱化。
- 纠正「基类去 `@Data` 是大型改造」的误判，实际残余工作仅个别模块少量类。

### 负面 / 风险

- **P2 存在性门禁可被空测试绕过**（已知弱点）：缓解措施为 CR 检查清单 + 测试模板 + 本 ADR 建议的「≥1 个 `@Test` 方法」强化；完全杜绝内容注水需人工评审，无法纯机器化。
- **「弱约束代码形式」可能被误读为「不约束」**：须在规范中同时写明保留的硬门禁（身份/租户保护）与行为判据（P2），避免放任。
- **身份早期确定依赖 ADR-0019**：在 ADR-0019 实现前，「构造期传入 id」并不真正稳定（会被 `insert` 覆盖），规范不得据此放宽。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 强制 `reconstitute()` + 调用点禁所有 `set*` | 测不到「是否绕过行为」，只会逼出双入口冗余；public setter 是 SDK 契约，禁用会大量误伤 |
| B. 依赖注入式「领域对象重建器」 | 引入新抽象与容器依赖，与「聚合应可在纯单测中构造」直接冲突 |
| C. 仅靠 CR 口头禁止 setter | 已被证伪：类型系统生成 setter，CR 挡不住进度压力 |
| D. 一次性全平台去 `@Data` | 误判规模；基类实际已达标，且会与「setter 可见性契约」混为一谈，引发不必要的破坏性变更 |

## 合规与迁移

1. **P2 门禁强化**：`BoneDddArchRules.aggregateRootsShouldHavePureUnitTest` 增加「测试类至少含 1 个 `@Test` 方法」判定（随 `bone-architecture-test` 提交，配正向/反向自测）。
2. **规范同步**：主规范 §17 增补「弱约束代码形式、强约束行为」判定口径；§12.1 补反贫血门禁映射（明确 `outerLayersMustNotMutateAggregateIdentity()` 为 A 类强制）；§16.2 修正基类已 `@Getter` 的现状并区分 setter 可见性与 `@Data` 两个问题。（已随 change `ddd-spec-v4-5-convergence` 完成）
3. **`@Data` 收敛（有界）**：残余集中在 `studio-generator` 约 5 个 domain 类（`DataSource`、`DataSourceConfig`、`DatabaseTable`、`TableColumn`、`CodeGenerationRequest` 等），按模块逐步替换为 `@Getter` + 手写业务 getter。因 SDK 走字段级反射，**不影响持久化**。列为后续专项，不阻塞本 ADR。
4. **身份早期确定**：待 ADR-0019 实现后，按 change `ddd-spec-v4-5-convergence` 任务 3.6 在 `bone-blueprint` 固化并加断言测试。

**回滚**：规则与文档变更，均无代码耦合；移除规则或还原文档即可。
