---
comet_change: metadata-engine-deep-refactor
role: technical-design
canonical_spec: openspec
---

# Design: metadata-engine-deep-refactor

> 关联 proposal：`openspec/changes/metadata-engine-deep-refactor/proposal.md`
> 前置：`refactor-metadata-engine-boundary-ddd`（已归档）已落地 spi/adapter/architecture 包重划与 SDK 接入。
> 2026-08-25 用户决策：**物理拆 4 个 Maven 子模块，删除 `bone-metadata-engine-core`**。
> 本文档固化拆分设计、代码归属映射与前置条件。

## 0. 范围决策（用户确认）

1. **物理拆 4 个 Maven 子模块**：`bone-metadata-engine-domain` / `bone-metadata-engine-ports` / `bone-metadata-engine-runtime` / `bone-metadata-engine-starter`。
2. **删除 `bone-metadata-engine-core`**：源码按边界分配到 4 模块后移除 core。
3. **"包内重划"废弃**：不再于 core 内重划，改为物理模块边界。
4. **T2/T5/T1.3 重构与拆分同步进行**：先归一重复类、再物理拆分（拆分前置条件见 §4）。

## 1. 目标模块结构与依赖方向

```
bone-metadata-engine
├── bone-metadata-engine-domain/      # 纯净领域：领域模型 + 纯算法，零 Spring 零 SDK
├── bone-metadata-engine-ports/       # 端口接口：spi/registry/repository 接口，仅接口
├── bone-metadata-engine-runtime/     # 适配器：SDK/Spring 实现，依赖 domain + ports
└── bone-metadata-engine-starter/     # Spring 自动装配
```

依赖方向：`domain（零依赖）← ports（仅接口，依赖 domain）← runtime（依赖 domain+ports）← starter（依赖 runtime）`。
`bone-metadata-server` 依赖 `engine-runtime`（或 starter），经端口消费领域能力。

## 2. 代码归属映射（基于实际 import 扫描）

### engine-domain（零 Spring 零 SDK）
- `model/`（重复实体待归一）、`metadata/`（除 3 个 Spring 类：MetadataRegistry/OperationRegistry/metadata.processor.CompositeMetadataProcessor）
- `annotation/`、`exception/`、`expression/`、`impact/`、`analysis/`（⚠️ 依赖 repository 接口，需倒置）、`core/`、`common/`、`cache/`、`multi/`、`event/`、`version/`、`processor/`（根包 MetadataProcessor 接口）
- 根包：`TransformationEngine`、`EngineConfiguration`、`RuleContext`、`DefaultFieldCalculationEngine`、`MetadataChangeType`

### engine-ports（仅接口，依赖 domain）
- `spi/`（MetadataRepositoryPort/MetadataPlatformBridge/Noop）
- `registry/`（MetadataRegistry 接口）、`platform/`（旧 MetadataPlatformBridge，**与 spi 重复待合并**）
- `repository/` 接口部分（MetadataRepository）
- `rule/BusinessRuleRegistry`（接口）、`validation/` 纯接口（ValidationResult/MetadataValidator/ValidationStrategy）
- `service/BusinessRuleEngine`、`service/DynamicDataService`（接口）
- 根包：`BusinessRuleEngine`、`FieldCalculationEngine`、`UnifiedRuleEngine`

### engine-runtime（依赖 SDK/Spring）
- `adapter/`（SdkMetadataRepository/IamMetadataBridge/MetaEntityConverter/InMemoryMetadataRepositoryPort/po）、`runtime/`、`security/`、`query/`（除 QueryAst）、`service/` 实现、`util/`、`context/`、`metadata/processor/`、`repository/InMemoryMetadataRepository`
- `rule/` 实现、`validation/` 实现（Spring 装配）、metadata 的 3 个 Spring 类（MetadataRegistry/OperationRegistry/CompositeMetadataProcessor）
- 根包：`MetadataEngine`（695 行编排核心）、`ExpressionEngine`、`RuleEngine`、`ValidationEngine`、`DefaultBusinessRuleEngine`、`MetadataChangedEvent`

### engine-starter（Spring 装配）
- `config/`（MetadataEngineAutoConfiguration/MetadataEngineInitializer/MetadataEngineProperties/RuleEngineConfig）
- `rule/RuleEngineAutoConfiguration`、`adapter/config/EngineSdkRepositoryConfig`、`security/SecurityConfig`、`BoneMetadataEngineApplication`

## 3. 拆分前置条件（必须先解决，否则拆分无法编译）

1. **归一 model/ ↔ metadata/ 重复实体**（T2）：两套 EntityMetadata/MetadataRegistry/BusinessRuleMetadata/SmartFieldMetadata/OperationMetadata 并存是最大障碍。先统一到 `metadata/*`（domain 模型），删除 `model/*`。
2. **合并重复端口**：`platform/` 与 `spi/` 的 `MetadataPlatformBridge` 合并为单一 ports 接口；`processor/` 与 `metadata/processor/` 的 `MetadataProcessor` 合并或分层。
3. **消除 domain 内依赖倒置**：`analysis/`、`metadata/OperationRegistry` 依赖 `repository.MetadataRepository` → 经已有 `MetadataImpactDataAccess`/spi 端口抽取为 domain 端口，repository 反向依赖 domain。
4. **去 Spring 化**（T1.3）：domain 算法包去 @Component/@Autowired；rule/validation/service 的 Spring 装配实现摘到 runtime。
5. **MetadataEngine 归 runtime**：其 695 行编排核心拆为 `application.MetadataEngineService`（若保留）后归 runtime；依赖 model→改 metadata（domain），platform→合并 ports。

## 3.1 T2 归一修正（2026-08-26，基于事实）

**原始假设"metadata/* 覆盖 model/*"不成立**：经代码核对，`model/*` 与 `metadata/*` 是"同语义、不同完成度、接口不兼容"的两套模型，部分类反而是 **model 完整、metadata 草稿**（例：`model.BusinessRuleMetadata` 25 字段含 triggerEvents/Builder/executionPhase，`metadata.BusinessRuleMetadata` 仅 8 字段）。

**修正策略**：合并**不以包方向一刀切**，而**以完整实现为准、逐类判断**：
- 对每对同名类，先确认哪套是**完整实现**（字段/方法覆盖），保留完整版。
- 删除不完整草稿版，将其**引用方改为完整版**，并**适配方法签名差异**（如 `isEnabled↔isActive`、`executionTiming↔executionPhase`、`FieldMetadata↔SmartFieldMetadata`）。
- 若两套接口差异过大无法安全合并，则**保留两者并行**（标注技术债），不强行合并导致功能丢失。
- 目标：**domain 内只有一套语义清晰、完整可用的领域模型**；无法安全合并的重复类列为已知债务，不阻塞物理拆分。

## 4. 与 server 的接入

`bone-metadata-server` 依赖 `engine-runtime`（含真实 adapter），经 `engine-starter` 自动装配或显式装配。server 提供 `@EnableSqlRepositories` 生成 `Repository<MetaEntityPo,Long>` bean 供 runtime 使用。

## 5. 明确不做

- 不新增 capability/业务特性。
- 不引入新 ORM/框架。
- 拆分后不改变对外 API 语义（server 调用方无感）。
- 不做多余模块拆分（严格 4 模块：domain/ports/runtime/starter）。
