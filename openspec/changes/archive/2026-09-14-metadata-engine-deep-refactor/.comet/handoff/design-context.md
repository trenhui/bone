# Comet Design Handoff

- Change: metadata-engine-deep-refactor
- Phase: design
- Mode: compact
- Context hash: c93278b64d24bb55163a46a764fdacf2ae877b23812958578117557f53a77b28

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/metadata-engine-deep-refactor/proposal.md

- Source: openspec/changes/metadata-engine-deep-refactor/proposal.md
- Lines: 1-44
- SHA256: 2b55a55cf9905ef379ef5d91f467341dc44eeca881fd35e3bea1bf0a8d73a7f2

```md
# Proposal: metadata-engine-deep-refactor

## Problem Statement

`bone-metadata-engine` 在边界重划（`refactor-metadata-engine-boundary-ddd`）后已完成 spi/adapter/architecture 包重划与 SDK 接入，
但仍残留三类**重度技术债**，阻碍后续维护与扩展：

1. **领域模型重复**：`model/*` 与 `metadata/*` 存在同名/相似类（`EntityMetadata`、`BusinessRuleMetadata`、`MetadataRegistry`、
   `OperationMetadata`、`SmartFieldMetadata` 等）。`model.EntityMetadata` 被 14+ 文件引用，`metadata.EntityMetadata` 独立一套，
   两套定义不一致，存在"同语义、双实现"的重复，易产生漂移。
2. **`MetadataEngine`（695 行 @Component）大聚合**：承载注册/缓存/影响分析/事件/配置等**多个职责**；含大量
   `invokeIfPossible()`/`invokeRepositoryMethod()` 等**返回 null 的反射兜底"简化实现"**；且同时引用 `model.EntityMetadata`
   与 `metadata.EntityMetadata` 两套模型。
3. **领域算法类仍带 Spring 注解**：`expression/`、`rule/`、`calculation/`、`validation/`、`transformation/`、`impact/`、`query/`
   中部分类使用 `@Component`/`@Autowired`，与 DDD 领域层纯净要求冲突。

## Success Criteria

- [ ] `model/*` 重复类全部删除，唯一领域模型收敛为 `metadata/*`（含 `EntityMetadata`/`SmartFieldMetadata`/`OperationMetadata`/`MetadataRegistry` 等），所有引用方改为 `metadata/*`，全量编译通过。
- [ ] `MetadataEngine` 拆分为 `application.MetadataEngineService`（应用层用例编排），`MetadataEngine` 变为轻量门面或移除；反射兜底"简化实现"清理，不再返回 null 掩盖错误；对外 API 语义保持一致。
- [ ] 领域算法类去除 Spring 注解，回归纯净领域；装配由应用层/Starter 完成。
- [ ] `engine-starter` 仅做装配（kernel + 真实 adapter），运行时 bean 装配验证通过。
- [ ] 全量测试（engine-core + server + ArchUnit）通过；新增真实数据源集成测试（H2）覆盖 `SdkMetadataRepository` 读已发布元数据。

## In Scope

- T2：`model/*` 与 `metadata/*` 重复类**彻底合并**（删除 `model/*` 全套，引用统一到 `metadata/*`）。
- T5：`MetadataEngine` 重构为 `application.MetadataEngineService`，逐方法迁移；清理反射兜底；统一双重 `EntityMetadata` 引用。
- T1.3：领域算法包（expression/rule/calculation/validation/transformation/impact/query）去除 Spring 注解，回归纯净领域。
- T6.1：`engine-starter` 装配收敛为 kernel + 真实 adapter，含运行时 bean 装配验证。
- T7：测试补全（adapter 单测、engine 集成测试、server 集成测试）+ README/文档更新。

## Out of Scope

- **不做物理 Maven 子模块拆分**（不拆 `engine-domain`/`engine-ports`/`engine-runtime`/`engine-starter` 四模块，保持在 `bone-metadata-engine-core` 内包重划）。
- 不新增能力（capability）或业务特性，纯重构。
- 不引入新 ORM/框架。
- `model/*` 与 `metadata/*` 之外的重复类（如 `service/` 与 `domain/` 的潜在重复）不在本 change 范围。

## Risks

- T2 大爆炸迁移：14+ 引用方改包，删除 model/* 后若有遗漏引用将编译失败。缓解：分包删除、每步全量编译验证、用 ArchUnit/IDE 引用定位。
- T5 重构：`MetadataEngine` 对外 API 被 `MetadataEngineAutoConfiguration`/`RuleEngineAutoConfiguration`/`DefaultFieldCalculationEngine` 等引用，迁移须保持语义。
- 领域算法去 Spring 注解后，原依赖注入点需在装配层重建，可能引入装配回归。

```

## openspec/changes/metadata-engine-deep-refactor/design.md

- Source: openspec/changes/metadata-engine-deep-refactor/design.md
- Lines: 1-87
- SHA256: bcbdfd1b9dbb28e4b865c376f7140250671d0df445e52d4e9ba82d09c7290f5b

[TRUNCATED]

```md
---
comet_change: metadata-engine-deep-refactor
role: technical-design
canonical_spec: openspec
---

# Design: metadata-engine-deep-refactor

> 关联 proposal：`openspec/changes/metadata-engine-deep-refactor/proposal.md`
> 前置：`refactor-metadata-engine-boundary-ddd`（已归档）已落地 spi/adapter/architecture 包重划与 SDK 接入。
> 本文档固化 2026-08-25 与用户确认的关键设计决策。

## 0. 范围决策（用户确认）

1. **重复类彻底合并（T2）**：删除 `model/*` 全套重复类，所有引用统一到 `metadata/*`（含 `EntityMetadata`/`SmartFieldMetadata`/`OperationMetadata`/`MetadataRegistry` 等）。增强属性（AI/计算/虚拟字段）本就承载在 `metadata/*`，合并后 `metadata/*` 为**唯一领域模型来源**。
2. **MetadataEngine 完整拆为 application service（T5）**：拆出 `application.MetadataEngineService` 应用层用例编排，逐方法迁移；清理 `invokeIfPossible`/`invokeRepositoryMethod` 等返回 null 的反射兜底；统一双重 `EntityMetadata` 引用。
3. **不做物理 Maven 子模块拆分**：保持 `bone-metadata-engine-core` 单模块，仅在 core 包内重划；不拆 `engine-domain/ports/runtime/starter` 四模块。
4. **不过度拆分**：`MetadataEngineService` 保持内聚，不按方法拆多个 service；仅在确有独立职责处建类（如缓存管理抽 `MetadataCacheManager`，若已存在则复用）。

## 1. 目标包结构（core 内，物理不拆）

```
com.bone.metadata.engine
├── application/          # 【新增】MetadataEngineService（应用层用例编排）
├── spi/                  # 端口（已存在）：MetadataRepositoryPort / MetadataPlatformBridge
├── adapter/              # 运行时（已存在）：SdkMetadataRepository / IamMetadataBridge / MetaEntityConverter / InMemoryMetadataRepositoryPort
├── architecture/         # ArchUnit 门禁（已存在）
├── metadata/             # 【唯一领域模型源】EntityMetadata / SmartFieldMetadata / OperationMetadata / MetadataRegistry / WorkflowMetadata
├── registry/             # MetadataRegistry（若 metadata.MetadataRegistry 为权威则收敛于此）
├── repository/           # 仓储接口 + InMemoryMetadataRepository（兼容）
└── domain/               # 领域算法包（expression/rule/calculation/validation/transformation/impact/query），去 Spring 注解
```

依赖方向：`domain（零依赖）← spi（仅接口）← adapter（SDK/Spring）← application（用例编排）← starter（Spring 装配）`。

## 2. T2 领域模型归一（彻底合并）

- **权威类**：`metadata/*`（已承载增强字段）。`model/*` 同名类（`EntityMetadata`/`BusinessRuleMetadata`/`MetadataRegistry`/`OperationMetadata`/`SmartFieldMetadata`/`FieldMetadata` 等）删除。
- **迁移顺序**（分步、每步全量编译验证，避免一次性破坏 14+ 引用）：
  1. 列出 `model/*` 与 `metadata/*` 每对同名类的字段差异，确认 `metadata/*` 字段覆盖 `model/*`。
  2. 先迁移引用方最少的类（如 `FieldMetadata`/`BusinessRuleMetadata`），再迁移 `EntityMetadata`（引用最多）。
  3. 每删一类，全局搜索该包引用改为 `metadata/*`，`mvn compile` 验证。
- **别名兼容**：若 `model.*` 有 `metadata.*` 缺失的字段，先补到 `metadata.*` 再删。
- **排除**：`model.*` 中与 `metadata.*` **非同名且无重复**的类（如仅 model 独有的算法辅助类）保留，不误删。

## 3. T5 MetadataEngineService 化

- **新增** `application.MetadataEngineService`（`@Service`），承担原 `MetadataEngine` 的用例编排：
  - 注册/查询/批量/重载实体元数据（`registerEntityMetadata`/`getEntityMetadata`/`batchGetEntityMetadata`/`reloadEntityMetadata`）。
  - 影响分析（`analyzeMetadataImpact`/`isCriticalImpact`）。
  - 依赖注入改为 `spi.MetadataRepositoryPort` + `spi.MetadataPlatformBridge`（经适配器），不再直接依赖旧 `repository.MetadataRepository`。
  - 缓存经 `MetadataCacheManager` 管理（若已存在复用；否则在 service 内部收敛）。
- **`MetadataEngine` 处置**：作为轻量门面保留对外 API（`MetadataEngineAutoConfiguration`/`RuleEngineAutoConfiguration`/`DefaultFieldCalculationEngine` 仍引用），内部委托 `MetadataEngineService`；或直接改为委托。
- **清理**：删除 `invokeIfPossible`/`invokeIfPossibleReturn`/`invokeRepositoryMethod` 等返回 null 的反射兜底；双重 `EntityMetadata` 引用统一为 `metadata.EntityMetadata`。

## 4. T1.3 领域算法去 Spring 注解

- `expression/rule/calculation/validation/transformation/impact/query` 中的 `@Component`/`@Autowired` 移除。
- 依赖注入改由 `application`/`starter` 装配（构造注入到 service，或显式工厂装配）。
- 每包独立验证编译；`architecture.EngineArchitectureTest` 增加"domain 零 Spring 注解"断言（若可行）。

## 5. T6.1 starter 装配收敛

- `MetadataEngineAutoConfiguration` 收敛为：kernel（`MetadataEngineService`）+ 真实 adapter（`SdkMetadataRepository`/`IamMetadataBridge`）+ 端口装配。
- `SdkMetadataRepository`/`IamMetadataBridge` 依赖 `Repository<MetaEntityPo, Long>` bean，由使用方（`bone-metadata-server`）`@EnableSqlRepositories` 提供；server 侧验证运行时装配。
- 若 server 无 `Repository` bean 导致启动失败，则回退为 `NoopMetadataPlatformBridge` + `InMemoryMetadataRepositoryPort` 默认装配，真实 adapter 由 server 显式装配。

## 6. 测试与门禁（T7/T8）

- **adapter 单测**：`SdkMetadataRepositoryTest`（已存在，Mockito）。
- **engine 集成测试**：新增 H2 数据源集成测试，`@EnableSqlRepositories` + `meta_entity`/`meta_field` 建表，验证 `SdkMetadataRepository` 读已发布元数据。
- **server 集成测试**：server 侧"经 engine 读已发布实体"。
- **ArchUnit**：现有 `ArchitectureTest` + `EngineArchitectureTest` 通过；必要时扩展 domain 纯净断言。

## 7. 风险与缓解

- **T2 删除 model/* 后的遗漏引用** → 分步删、每步 `mvn clean compile` 全量验证。
- **MetadataEngine API 语义保持** → 门面委托，不改对外签名。
- **去 Spring 注解后的装配回归** → 装配集中到 application/starter，单测覆盖。
- **不过度拆分** → service 内聚，仅抽确有独立职责的类。

```

Full source: openspec/changes/metadata-engine-deep-refactor/design.md

## openspec/changes/metadata-engine-deep-refactor/tasks.md

- Source: openspec/changes/metadata-engine-deep-refactor/tasks.md
- Lines: 1-36
- SHA256: 2f1346cbc541ea4b6609aba156d160d3a6bf01e68970359f4910384bd38e4f94

```md
# Tasks: metadata-engine-deep-refactor

> 配合 `proposal.md` / `design.md`。策略：**包内重划，不物理拆 Maven 子模块**；分步执行、每步全量编译验证。

## T2 领域模型归一（彻底合并 model/* → metadata/*）
- [ ] T2.1 盘点 `model/*` 与 `metadata/*` 每对同名类字段差异，确认 `metadata/*` 字段覆盖 `model/*`（补缺失字段）
- [ ] T2.2 迁移引用方最少的类（如 `FieldMetadata`/`BusinessRuleMetadata`/`OperationMetadata`/`SmartFieldMetadata`）：删 `model/*`，引用改 `metadata/*`，全量编译验证
- [ ] T2.3 迁移引用最多的 `model.EntityMetadata`：删 model 版，14+ 引用改 `metadata.EntityMetadata`，全量编译验证
- [ ] T2.4 清理 `model/*` 中与 `metadata/*` 非重复的独有类（保留，不误删），最终删除空 `model` 包或收敛剩余类

## T5 MetadataEngine → application service
- [ ] T5.1 新增 `application.MetadataEngineService`（`@Service`），迁移注册/查询/批量/重载实体元数据 + 影响分析方法
- [ ] T5.2 `MetadataEngineService` 依赖改为 `spi.MetadataRepositoryPort` + `spi.MetadataPlatformBridge`；缓存经 `MetadataCacheManager`（复用或收敛）
- [ ] T5.3 清理 `invokeIfPossible`/`invokeIfPossibleReturn`/`invokeRepositoryMethod` 等返回 null 的反射兜底；统一双重 `EntityMetadata` 为 `metadata.EntityMetadata`
- [ ] T5.4 `MetadataEngine` 改为轻量门面（保留对外 API，委托 `MetadataEngineService`），`MetadataEngineAutoConfiguration`/`RuleEngineAutoConfiguration`/`DefaultFieldCalculationEngine` 引用不受破坏，全量编译验证

## T1.3 领域算法去 Spring 注解
- [ ] T1.3.1 盘点 `expression/rule/calculation/validation/transformation/impact/query` 中带 `@Component`/`@Autowired` 的类，逐包去注解
- [ ] T1.3.2 依赖注入改由 application/starter 装配（构造注入 service），每包全量编译验证
- [ ] T1.3.3 `EngineArchitectureTest` 扩展 domain 零 Spring 注解断言

## T6.1 starter 装配收敛
- [ ] T6.1.1 `MetadataEngineAutoConfiguration` 收敛为 kernel + 真实 adapter（`SdkMetadataRepository`/`IamMetadataBridge`）
- [ ] T6.1.2 server 侧 `@EnableSqlRepositories` 提供 `Repository` bean 的运行时装配验证；若无则回退 Noop + InMemory 默认装配

## T7 测试与文档
- [ ] T7.1 adapter 单测 `SdkMetadataRepositoryTest` 保持全绿（随 model 合并调整 import）
- [ ] T7.2 新增 engine 集成测试（H2 + `@EnableSqlRepositories` + meta_* 建表，验证 `SdkMetadataRepository` 读已发布元数据）
- [ ] T7.3 server 侧"经 engine 读已发布实体"集成测试
- [ ] T7.4 更新 `bone-metadata-engine/README.md`（application/domain 包说明）
- [ ] T7.5 更新 `doc/design/modules/9. SmartMeta 引擎模块技术说明.md`

## T8 门禁与收尾
- [ ] T8.1 全量 `mvn spotless:apply` + ArchUnit（`ArchitectureTest` + `EngineArchitectureTest`）通过
- [ ] T8.2 全量测试（engine-core + server + 新增集成测试）通过
- [ ] T8.3 Comet Classic verify → archive

```
