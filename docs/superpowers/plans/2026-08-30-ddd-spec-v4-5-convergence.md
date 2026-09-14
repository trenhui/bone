---
comet_change: ddd-spec-v4-5-convergence
status: ready
base_ref: c6a07547
workflow: full
language: zh-CN
design_doc: openspec/changes/ddd-spec-v4-5-convergence/design.md
tasks: openspec/changes/ddd-spec-v4-5-convergence/tasks.md
archived-with: 2026-09-11-ddd-spec-v4-5-convergence
---

# 实施计划：v4.5 DDD 规范收敛 — D11/D12 两条评审修订并入

> 本计划由 build 阶段防护（COMET PHASE GUARD）要求先行登记。本 change 已确认「先只做 Design」，执行范围止于设计产物回写，**不修改 SDK / 业务模块源码**；ADR-0014 的实际 SDK 代码改动与 D12 的 `@Data` 收敛延后至执行对应 ADR 的后续 change。

## 事实基线（build 阶段核实，子代理量化 + 代码核对）

### D11 相关事实
- **既有模式已满足「身份在构造期确定」**：`DistributedIdGenerator`（`bone-core/util`，静态工具，含 `generateLongId()`）已被 blueprint 应用层使用：`long orderId = DistributedIdGenerator.generateLongId(); Order.create(orderId, ...)`——id 作为聚合构造参数传入，构造后即有稳定身份。
- **`IdGenerator`（`bone-core/domain/id`）+ `DefaultIdGenerator`（SDK `sql/executor`）是 SDK 保存时生成 id 的 SPI**，不是给应用层的端口；在其上再包一层 `domain/gateway/*IdGenerator` 属单一实现的纯仪式，违反 D4「应用层收敛」精神。
- **SDK `BaseRepository.insert()` 真实形态（核心差距）**：第 164-167 行在非 IDENTITY 分支**无条件** `generateId + setEntityId`，会覆盖调用方已传入的非空 id；而 `batchInsert`（第 196 行，已 `if (e.getId() == null)`）与 `save` 依赖的 `ensureIdInitialized`（第 658 行，已 `if (entity.getId() == null)`）均尊重非空 id。→ ADR-0014 的核心就是让 `insert` 与二者一致。
- **ADR-0014 影响面（实测，低风险）**：全平台 `insert()` 直接调用点仅 **20 处**（masterdata 3 / extension-studio 9 / studio-generator 2 / metadata-server 3）；主体写路径走 `save`（132 处）/ `batchInsert`（4 处）；**未发现任何「先 set 非空 id 再 insert 依赖覆盖」的反模式**；受影响实体主键策略全部为 `DISTRIBUTED_ID`（IDENTITY 仅 SDK 内部 1 处，UUID/SEQUENCE 生产 0 处）。故修复对现有 null-id 调用方**零行为变化**、仅新增对预置 id 的尊重。

### D12 相关事实
- **聚合根基类已达标**：`AggregateRoot` / `TenantAggregateRoot` 当前已仅用 `@Getter`（非 `@Data`），`setId`/`setTenantId` 为手写（SDK 反射回填 + `Tenantable` 契约），全平台 167+ 依赖使其不可降级为 protected。
- **6/8 模块 domain 实体已改用 `@Getter`**；真正残留 `@Data` 仅 `studio-generator` 约 5 个 domain 类（`DataSource`/`DataSourceConfig`/`DatabaseTable`/`TableColumn`/`CodeGenerationRequest`）。
- **SDK 字段回填走字段级反射**：`BaseRepository.java:674` 的 `ReflectionUtil.setFieldValue(entity, primaryKey.getFieldName(), converted)` 不依赖 Lombok 生成的 setter——故去 `@Data` 不影响持久化。
- **强制 `reconstitute()` + 调用点 ArchUnit 硬门禁的缺陷**：实测该写法**测不到「是否绕过行为」**，只会逼出 `create()` + `reconstitute()` 双入口冗余；`outerLayersMustNotMutateAggregateIdentity()` 继续作为风格约束保留（M2 已降为可选），非硬门禁。

## 执行步骤（对齐 tasks.md M3；本次 build 仅完成 P1–P2，P3–P5 为后续 design-only 草稿）

### 阶段 A：设计产物回写（本次 build 到此，停在执行前）
- [P1] 回写 `openspec/changes/ddd-spec-v4-5-convergence/design.md`：
  - D11 删除「第一步新增 `domain/gateway/*IdGenerator` 端口」，改为「固化既有 `DistributedIdGenerator` 模式 + 测试锁定」+ 第二步 ADR-0014（insert 尊重非空 id），并附实测低风险管理面。
  - D12 改为「弱约束代码形式、强约束行为」五点杠杆排序，去掉强制 `reconstitute()` 与调用点 ArchUnit 硬门禁；明确 ORM 恢复走 SDK 字段级反射、基类去 `@Data` 分阶段（仅 studio-generator 5 类）。
  - 同步更新：风险表 D11 行（降级为低风险）、ADR 清单 ADR-0015 描述、Open Question 1（可行性已实测确认）。
- [P2] 回写 `tasks.md`：3.1（ADR-0014 可行性已实测确认、免独立 spike）、3.2（ADR-0014 草稿，SDK 代码改动延后）、3.3（ADR-0015 收敛方向）、3.6（blueprint 固化模式 + 断言测试，不新增端口）。
- 验收：`openspec validate --change ddd-spec-v4-5-convergence` 通过；design.md 与 tasks.md 内部一致、无 D11 端口残留。

### 阶段 B：M3 ADR 草稿产出（design-only，不写源码）
- [P3] 起草 ADR-0014：insert 尊重非空 id。含动机、影响模块（20 处 insert 调用方）、迁移路径（单点改 `BaseRepository.insert` 分支）、回滚（还原 `insert` 即回退）。
- [P4] 起草 ADR-0015：弱约束代码形式、强约束行为。含现状实测（基类已 `@Getter`、SDK 走 `ReflectionUtil.setFieldValue`、studio-generator `@Data` 清单）与分批收敛方案。
- [P5] 在 `bone-blueprint` 增加断言测试（纯 JUnit），锁定「聚合构造后即刻持有非空稳定 id、领域事件可安全携带 id」不变量；既有 136 测试仍全绿。

### 阶段 C：执行前停止
- **不修改** `BaseRepository.insert()`、**不修改**任何业务模块源码。ADR-0014 的 SDK 代码改动与 D12 的 `@Data` 收敛，列入后续执行对应 ADR 的 change 再做。

## 关键风险
- **阶段防护**：build 阶段写 `design.md`/`tasks.md` 前必须先 `comet state set ... plan` 登记本计划（base-ref `c6a07547`），否则 COMET PHASE GUARD 拦截。
- **ADR-0014 实际落地风险**：实测低风险，但执行该 ADR 时仍应先 blueprint 验证 + 全量 `mvn clean install` 回归，杜绝跨模块副作用。
- **D12 `@Data` 收敛范围**：仅 studio-generator 约 5 类，分阶段替换 `@Data` → `@Getter` + 手写业务 getter，不影响持久化（SDK 字段级反射）。
- **范围控制**：本 change 严格 design-only；任何 SDK/业务源码改动均属越界，须停在当前阶段。
