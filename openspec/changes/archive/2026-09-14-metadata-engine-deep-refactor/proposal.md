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

- **物理拆 4 个 Maven 子模块**：`bone-metadata-engine-domain` / `engine-ports` / `engine-runtime` / `engine-starter`，**删除 `bone-metadata-engine-core`**（源码按边界分配到 4 模块）。
- T2：`model/*` 与 `metadata/*` 重复类**彻底合并**（删除 `model/*` 全套，引用统一到 `metadata/*`，作为 domain 模型）。
- 合并重复端口（`platform/` 与 `spi/` 的 `MetadataPlatformBridge`）、消除 domain 内依赖倒置、domain 算法去 Spring 注解。
- T5：`MetadataEngine` 归入 runtime，清理反射兜底；统一双重 `EntityMetadata` 引用。
- T7：测试补全（adapter 单测迁移、engine 集成测试、server 集成测试）+ README/文档更新。

## Out of Scope

- **不再于 core 内"包内重划"**（该策略废弃，改为物理拆 4 模块）。
- 不新增能力（capability）或业务特性，纯重构。
- 不引入新 ORM/框架。
- `model/*` 与 `metadata/*` 之外的重复类（如 `service/` 与 `domain/` 的潜在重复）不在本 change 范围。

## Risks

- T2 大爆炸迁移：14+ 引用方改包，删除 model/* 后若有遗漏引用将编译失败。缓解：分包删除、每步全量编译验证、用 ArchUnit/IDE 引用定位。
- T5 重构：`MetadataEngine` 对外 API 被 `MetadataEngineAutoConfiguration`/`RuleEngineAutoConfiguration`/`DefaultFieldCalculationEngine` 等引用，迁移须保持语义。
- 领域算法去 Spring 注解后，原依赖注入点需在装配层重建，可能引入装配回归。
