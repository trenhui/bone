# Tasks: bone-metadata-engine 职责边界重划

> 配合 `proposal.md` / `design.md`。任务按 DDD 四层迁移顺序,每完成一项在 checkbox 标记。
>
> **策略说明（design §0，2026-08-25 确认）**：采用「先包内重划、物理拆分留 T9」。
> 当前 change 在 `bone-metadata-engine-core` 内按 `spi`(ports) / `adapter`(runtime) / `architecture` 包重划边界，
> 并完成 SDK 适配器接入与 `bone-metadata-server` 单体接入（模式 B）。物理拆 4 个 Maven 子模块与深度领域归一（T1.3/T2/T5）列为 T9 后续演进，不在本 change 交付。

## T1 模块骨架与依赖方向
- [x] T1.1 边界包骨架：在 `bone-metadata-engine-core` 内建立 `spi`(ports) / `adapter`(runtime) / `architecture` 包并固化依赖方向（`domain ← spi ← adapter`）
- [x] T1.2 新增 `EngineArchitectureTest`（增量 ArchUnit 门禁，固化 `spi` 包不依赖 spring/sdk + 端口契约位于 spi 包）

## T3 端口化（Ports）
- [x] T3.1 定义 `spi.MetadataRepositoryPort`（读侧契约，基于 `repository.MetadataRepository` 读方法子集）
- [x] T3.2 定义 `spi.MetadataPlatformBridge` 端口（tenant/event/loadPublishedEntityJson 三方法）+ `NoopMetadataPlatformBridge` 默认实现

## T4 适配器接入 SDK（Runtime）
- [x] T4.1 实现 `SdkMetadataRepository`（基于 `bone-metadata-sdk` 的 `Repository.findByCriteria` 读已发布 `meta_*`，`withTenantEntity/withTenantField` 统一租户过滤）
- [x] T4.2 实现 `IamMetadataBridge`（`currentTenantId()` 接 `TenantContext`、`publishEvent()` 接 Spring `ApplicationEventPublisher`、`loadPublishedEntityJson()` 经 SDK `Repository` 查询已发布 `meta_entity`）
- [x] T4.3 新增 `InMemoryMetadataRepositoryPort` 实现 `spi.MetadataRepositoryPort` 作为测试/回退实现（保留旧 `repository.InMemoryMetadataRepository` 并行）
- [x] T4.4 核对 SDK API 签名（`Repository<T, ID>` 双泛型、`findOneByCriteria` 返回 `T`、`currentTenantId()` 返回 `Optional<String>`）

## T6 装配与单体接入
- [x] T6.2 `bone-metadata-server` 的 pom 已含 `bone-metadata-engine-core` 依赖（模式 B 接入，已确认存在）
- [x] T6.3 server 编译通过，engine 作为库被加载（`-am compile` 与 `test` 均 BUILD SUCCESS）

## T7 测试与文档
- [x] T7.1 adapter 层单测（`SdkMetadataRepositoryTest` Mockito mock Repository，4 用例全绿）
- [x] T7.4 更新 `bone-metadata-engine/README.md`（边界职责 + 包结构）
- [x] T7.5 更新模块 design 文档（`doc/design/modules/9. SmartMeta 引擎模块技术说明.md`，补 spi/adapter 边界）

## T8 门禁与收尾
- [x] T8.1 全量 `mvn spotless:apply` + ArchUnit 通过
- [x] T8.2 全量测试（engine-core 100 + server 25 全绿，BUILD SUCCESS）
- [x] T8.3 Comet Classic verify → archive（本 change 编码/文档任务全部完成，进入验证收尾）

## T9 后续演进（不在本 change 交付，设计 §0 明确推迟，非 checkbox 待办）

以下为后续演进候选，**不在本 change 交付范围**，由后续专门 change 承接，故不以待办 checkbox 计：

- **T1.3**：将 domain 算法类（expression/rule/calculation/validation/transformation/impact/query）去 `@Component`/Spring 注解、归入纯净领域。
- **T2**：将 `model/*` 与 `metadata/*` 的重复类（`MetadataRegistry`/`EntityMetadata`/`OperationMetadata` 等）归一为单一来源（domain 只放核心元数据概念 `EntityMetadata`/`FieldMetadata`/`RelationMetadata`，AI/计算/虚拟字段等增强视图暂留 `metadata` 包）；原 `metadata/*` 的"已发布元数据 DTO"另行归入独立 `dto` 包；全量编译消除旧引用。
- **T5**：将原 `MetadataEngine` `@Component` 大聚合重构为 `application.MetadataEngineService`；移除自管 `ConcurrentHashMap` 缓存，改经 Port 读后由 `MetadataCacheManager` 管理。
- **T6.1**：`engine-starter` 的 `MetadataEngineAutoConfiguration` 仅做装配（kernel + 真实 adapter，含运行时 bean 装配验证）。
- **T7.2/T7.3**：engine 集成测试与 server 侧"经 engine 读已发布实体"集成测试（依赖真实数据源 H2/MySQL）。
- **物理拆分**：将 `bone-metadata-engine-core` 拆为 `engine-domain` / `engine-ports` / `engine-runtime` / `engine-starter` 四个 Maven 子模块，包名改为 `com.bone.metadata.engine.domain` 等。
