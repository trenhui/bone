# Implementation Plan: metadata-engine-boundary-ddd

> Comet Classic `full` workflow / `/comet-build` 阶段实施计划
> change: `refactor-metadata-engine-boundary-ddd`（phase: build）
> 配套产物：OpenSpec `proposal.md` / `design.md` / `tasks.md`；Superpowers Design Doc `docs/superpowers/design/metadata-engine-boundary-ddd.md`
> 本计划把 `tasks.md` 的 T4–T8 拆成可顺序执行、可验证的步骤。**T1.2 / T3.1 / T3.2 已在 design 阶段奠基（spi 包端口 + ArchUnit 门禁已建且 3/3 通过）**，不重复。

## 执行原则

- 每次改动确保 `bone-metadata-engine-core` 编译通过 + spotless 通过。
- 每完成一个可验证里程碑（如 T4 完成）即跑 `mvn -pl bone-metadata-engine/bone-metadata-engine-core test` 验证 ArchUnit + 编译。
- 依赖方向严格遵守 design.md Decision 5：`domain ← ports ← runtime ← starter`，domain 零框架/零 SDK 依赖。

## 阶段 A：T4 SDK 适配器接入（本回合核心，最高优先级）

**A1 — 核对 SDK API（design §4 标注项，先决）**
- 读取 `bone-metadata-sdk` 的 `meta_entity` / `meta_field` PO（`@Table` 名、字段、`extends Entity<ID>` 类型）。
- 确认 `Repository.findByCriteria(Criteria)` / `pageByCriteria` 真实签名、`Criteria` 构造方式（字段过滤 / 分页 / 排序）。
- 确认多租户字段 `tenant_id` 如何经 `TenantContext` 注入（SDK Repository 是否自动带 tenant 过滤）。
- 产出：一份 PO 字段 ↔ engine `EntityMetadata` 字段的映射表（写进本计划附录或 design.md）。

**A2 — 实现 `SdkMetadataRepository`（新文件 `adapter/SdkMetadataRepository.java`）**
- 包：`com.bone.metadata.engine.adapter`（runtime 层，可依赖 SDK）。
- 实现 `spi.MetadataRepositoryPort` 读侧方法。
- 内部：构造 `Criteria` 查 `meta_entity` 原始表 → 用 SDK `Repository` 查询 → 转换为 engine `EntityMetadata` / `FieldMetadata` / `RelationMetadata`（适配转换，domain 不感知 SDK）。
- 多租户：经 `IamMetadataBridge.currentTenantId()` 取租户，构造查询时带入（若 SDK 不自动带）。

**A3 — 平台桥接接真**
- 将现有 `platform.MetadataPlatformBridge` 改为 `implements spi.MetadataPlatformBridge` 并迁移至 `spi` 包（消除同名重复，design.md review Q4）；或新建 `IamMetadataBridge` 实现 spi 端口：
  - `currentTenantId()` → 接 `TenantContext`。
  - `loadPublishedEntityJson(tenantId, entityCode)` → 经 SDK 查已发布 `meta_entity` 序列化的 JSON。
  - `publishEvent(eventJson)` → 接 Spring `ApplicationEventPublisher`（@TransactionalEventListener 异步）。
- 默认 `NoopMetadataPlatformBridge`（已建）保留为回退。

**A 里程碑验证**：`mvn -pl ... test` 通过；新增 `SdkMetadataRepositoryTest`（内存构造 `meta_entity` PO + stub TenantContext）验证读 + 转换；ArchUnit `EngineArchitectureTest` 通过（adapter 包可依赖 SDK，不破坏 spi 纯净）。

## 阶段 B：T2 领域模型归一
- 消除 `model/*` 与 `metadata/*` 的重复类（如 `EntityMetadata` / `FieldMetadata` / `RelationMetadata` 双份），统一到 `domain/model/*`。
- 更新 `MetadataRepositoryPort` 返回类型指向统一模型。
- 扩充 ArchUnit：`noClasses().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAnyPackage("org.springframework..","com.bone.metadata.sdk..")`。
- 验证：编译 + ArchUnit。

## 阶段 C：T5 `MetadataEngine` 重构为 Application Service
- 拆分 `MetadataEngine` 大 `@Component`（400+ 行）为 application service：命令/查询 handler 或领域服务调用 `MetadataRepositoryPort`。
- `MetadataEngine` 降级为 facade（仅编排 + 兼容性入口）。
- 验证：编译 + 现有表达式/规则引擎测试不回归。

## 阶段 D：T6 `bone-metadata-server` 模式 B 真正接入
- `bone-metadata-server` 的 `pom.xml` 增加 `bone-metadata-engine-core` 依赖。
- 装配 `SdkMetadataRepository` + `IamMetadataBridge`（starter 包 `@AutoConfiguration`），server 现可读取 SDK 已发布元数据供引擎运行。
- 验证：server 编译通过 + 一个集成测试证明引擎能经 SDK 拿到已发布实体。

## 阶段 E：T7 测试 + T8 门禁收尾
- T7：补齐 adapter 单元测试、engine 集成冒烟测试、`SdkMetadataRepositoryTest`。
- T8：补 `design_doc` 最终状态、`tasks.md` 全勾选、README/迁移 note；接入 `BoneDddArchRules` 全套门禁（若 B 完成）。
- 收尾：`comet state transition <name> build-complete` → verify 阶段。

## 风险与回退
- A1 若 SDK `Repository` 泛型约束导致无法用 `meta_entity` PO 直接查（如 PO 未 `extends Entity`），改用 SDK 的 `SqlBuilder` 原生查询 + 手动映射（仍封装在 adapter 包）。
- 物理拆 4 Maven 模块（原 T1.1）已降为后续可选（design.md Decision 1），不在本计划强制。
