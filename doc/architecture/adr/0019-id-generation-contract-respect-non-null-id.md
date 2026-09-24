# ADR-0019：ID 生成契约 — SDK 尊重非空 id（`insert` 仅在 id 为空时生成）

| 项 | 内容 |
|----|------|
| **状态** | 已接受（2026-09-05 实现：`BaseRepository.insert()` 非 IDENTITY 分支已改为「空才生成」；单测 `testInsert_ShouldRespectPresetNonNullId` / `testInsert_ShouldGenerateIdWhenNull` 随 SDK 合入，18/18 通过） |
| **日期** | 2026-08-30（提议）；2026-09-05（实现） |
| **决策者** | 平台架构组 |
| **关联** | [Bone-DDD-最终实践方案 §18.1](../Bone-DDD-最终实践方案.md)；change `ddd-spec-v4-5-convergence`（D11） |

---

## 背景

`bone-metadata-sdk` 的 `BaseRepository` 在主键生成上存在**三条相互不一致的路径**：

| 路径 | 位置 | 行为 |
|---|---|---|
| `insert()` 非 `IDENTITY` 分支 | `BaseRepository.java` L164-167 | **无条件** `generateId` + `setEntityId`，**覆盖**调用方已传入的非空 id |
| `batchInsert()` | `BaseRepository.java` L196 | `if (e.getId() == null)` 才生成 |
| `ensureIdInitialized()`（`save()` 依赖） | `BaseRepository.java` L658 | `if (entity.getId() == null)` 才生成 |

后果：

1. **内存态 id ≠ 持久化后 id**。应用层预分配并传入聚合构造的 id，会在 `insert` / `save` 时被**静默替换**为 SDK 新生成的值，调用方无任何提示。
2. **「身份在构造期确定」无法实现**。领域事件若在构造/行为阶段携带 id，该 id 与落库 id 不一致，下游按 id 关联即失效。
3. **跨实体外键错位风险**。`bone-blueprint` 的 `CreateOrderCommandHandler` 以预分配 `orderId` 构造 `Order`，并以同一 `orderId` 作为 `OrderItem` 的 `orderId`；而 `Order` 实际落库 id 已被改写。
4. **外部 id 导入场景被阻断**。主规范 §18.1 例外②要求「保留外部 id」须经 SDK 扩展支持，根因即本条。
5. **`saveAll()` 存在潜在缺陷**：`toInsertFromUpdate`（id 非空但库中不存在）若经 `insert` 落库，其既有 id 亦会被覆盖。

## 决策

将 `BaseRepository.insert()` 的非 `IDENTITY` 分支改为**仅在 id 为空时生成**，与 `batchInsert()` / `ensureIdInitialized()` 语义一致——**空才生成，非空则尊重**：

```java
if (strategy != GenerationStrategy.IDENTITY) {
  if (entity.getId() == null) {              // ← 新增：尊重调用方预置的非空 id
    Object generatedId = sqlExecutor.generateId(strategy, entity);
    setEntityId(entity, generatedId);
  }
  BatchCompiledQuery batch =
      sqlBuilder.buildBatchInsert(entityClass, Collections.singletonList(entity));
  sqlExecutor.batchUpdate(batch);
}
```

`IDENTITY` 分支保持不变（自增主键本应由数据库赋值）。

**明确本 ADR 是「身份在构造期确定」的先决条件**，而非可选项：只有实现本 ADR 后，应用层才可安全地预生成 id 并作为聚合构造参数传入。

## 理由

- **一致性优先**：同一 SDK 内三条写入路径对同一语义（是否生成主键）给出两种答案，属实现缺陷而非设计取舍。
- **风险已实测可控**：全平台 `insert()` 直接调用点仅 20 处，且**未发现任何「先 set 非空 id 再依赖 insert 覆盖」的反模式**；受影响实体主键策略**全部**为 `DISTRIBUTED_ID`（`IDENTITY` 仅 SDK 内部 1 处，`UUID` / `SEQUENCE` 生产 0 处）。
- **改造面极小**：单点修改 `insert()` 的一个分支，无 API 变更、无调用方改造。
- **收益确定**：解锁外部 id 导入、身份早期确定、事件载荷可携带 id，并消除 `saveAll()` 的 id 覆盖隐患。

## 后果

### 正面

- 应用层可安全预生成 id，实现「身份在构造期确定」，领域事件载荷可携带有效 id。
- 外部 id 导入 / 数据迁移场景自然成立，可移除 §18.1 例外②。
- 三条写入路径语义统一，消除「看似传了 id 却被静默替换」的隐蔽陷阱。
- 修复 `saveAll()` 中 `toInsertFromUpdate` 的 id 被覆盖问题。

### 负面 / 风险

- **对现有 null-id 调用方零行为变化**：所有存量调用方均为「id 为空、依赖 SDK 生成」，修复后逻辑完全不变（实测 20 处调用点、0 反模式）。
- **新风险：调用方传入重复 id 将直接落库并可能主键冲突**。这是「尊重非空 id」的应有语义，但须在 SDK Javadoc 明确：调用方对预置 id 的唯一性负责。
- **`IDENTITY` 分支不受影响**：自增主键仍由数据库赋值；若调用方对 `IDENTITY` 实体预置 id 仍会被覆盖（符合预期）。
- **实现前不得放宽规范**：在 `insert()` 修复实际合入前，主规范 §18.1「应用层不预分配 id」必须保持，§18.1 例外②不得删除。

## 备选方案

| 方案 | 未采纳原因 |
|------|------------|
| A. 新增应用层 `domain/gateway/*IdGenerator` 端口 | 现有 `DistributedIdGenerator`（`bone-framework/bone-core` 的 util 包静态工具）已满足需求，再包一层属单一实现的纯仪式；且不解决 `insert` 覆盖 id 的根因 |
| B. 保持现状，仅靠 §18.1 例外 + README 登记 | 不解决根因；「身份在构造期确定」永远无法实现，静默替换的陷阱持续存在 |
| C. 新增 `insertWithId(entity)` 独立方法 | API 增殖，调用方需感知两种插入语义；同一语义不应有两个入口 |
| D. 仅在 `save()` 路径修复，`insert()` 保持 | 加剧路径间不一致，`insert()` 直接调用方（20 处）行为仍不可预期 |

## 合规与迁移

**影响模块（实测）**：全平台 `insert()` 直接调用点 **20 处**

| 模块 | 调用点 | 主要文件 |
|------|--------|----------|
| bone-extension-studio | 9 | `MetadataPluginExecutionLogRepository`、`MetadataPluginVersionRepository`、`MetadataStudioAuditRepository`、`MetadataExtensionRepository`、`MetadataExtPointRepository` |
| bone-masterdata | 3 | `PerformDataQualityCheckHandler`、`ConvertFromBusinessEntityHandler` |
| bone-metadata-server | 3 | `CreateMetaEntityHandler`、`CreateMetaFieldHandler`、`CreateMetaRelationHandler` |
| studio-generator | 2 | `SyncTableMetadataHandler`、`CreateDataSourceHandler` |

（bone-iam / bone-system / bone-integration 无 `insert()` 直接调用；主体写路径走 `save()` 132 处 / `batchInsert()` 4 处，已具备正确语义。）

**迁移步骤**

1. ✅ 在 `bone-metadata-sdk` 修改 `insert()` 非 `IDENTITY` 分支（单点），补充单元测试：① id 为空 → 生成；② id 非空 → 保持不变。（2026-09-05 完成）
2. ⏳ 在 `bone-blueprint` 先行验证：全量测试（136 个）通过。
3. ⏳ 全平台 `mvn clean install` 回归。
4. 实现后，方可放宽主规范 §18.1「应用层不预分配 id」，并移除例外②。
5. 实现后，方可执行 change `ddd-spec-v4-5-convergence` 任务 3.6（在 blueprint 固化「构造期 id == 持久化后 id」并加断言测试）。

**回滚**：还原 `insert()` 的该分支即可（无数据结构变更、无调用方改造、无 API 变更）。

**前置依赖**：无。
**后续依赖**：本 ADR 是「身份在构造期确定」与 blueprint 明细持久化（`OrderItem` 外键正确性）的前置条件。
