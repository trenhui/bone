---
comet_change: refactor-metadata-engine-boundary-ddd
role: technical-design
canonical_spec: openspec
---

# Design: bone-metadata-engine 职责边界重划

> 配套 `proposal.md`。本文件细化 DDD 包结构、端口契约、与 `bone-metadata-sdk` 的接口映射。

## 0. 实现策略调整（design 阶段细化）

原 proposal/design §1 规划"新建 4 个 Maven 子模块"。经 design 阶段 SDK API 核对（design §4 标注项），发现：

- `bone-metadata-sdk` 的 `Repository<T extends Entity<ID>>` 是泛型，engine 的 `EntityMetadata` 是独立 POJO。若让领域模型继承 sdk `Entity`（物理拆模块 + 强耦合）属**侵入式大爆炸**，风险高。
- engine 当前代码（156 文件）尚未分层，直接套 `BoneDddArchRules` 全套规则会立即红。

**调整为：先包内重划（低风险、可渐进、不破坏现有构建），物理拆 Maven 模块作为后续可选。** 在 `bone-metadata-engine-core` 内按 `domain / spi(ports) / adapter(runtime)` 包重划，加 ArchUnit 增量门禁固化边界。这同样达成"融入单体 + 边界清晰"目标，且本回合即可产出可编译增量。

> 物理拆 4 模块（engine-domain / engine-ports / engine-runtime / engine-starter）保留为 T9 后续项，待包内重划稳定后按需执行。

### 0.1 本回合已落地（design 奠基增量）

- `com.bone.metadata.engine.spi.MetadataRepositoryPort`：读侧端口契约（基于现有 `repository.MetadataRepository` 读方法子集，返回引擎现有 `metadata.*` 模型）。
- `com.bone.metadata.engine.spi.MetadataPlatformBridge`：平台桥接端口（租户/事件/已发布实体加载），语义对齐原 `platform.MetadataPlatformBridge`。
- `com.bone.metadata.engine.spi.NoopMetadataPlatformBridge`：默认空实现（接真由 T4 `IamMetadataBridge` 提供）。
- `com.bone.metadata.engine.architecture.EngineArchitectureTest`：增量 ArchUnit 门禁（spi 不依赖 spring/sdk + 端口契约位于 spi 包）。

## 1. 目标包结构（最终态，包内重划版）

```
bone-metadata-engine/
├── bone-metadata-engine-domain/        # artifactId: bone-metadata-engine-domain
│   └── com.bone.metadata.engine.domain
│       ├── expression/                 # ExpressionEngine(纯算法,迁自 engine/ExpressionEngine)
│       ├── rule/                       # RuleEngine / BusinessRuleEngine
│       ├── calculation/                # FieldCalculationEngine
│       ├── validation/                 # ValidationEngine
│       ├── transformation/             # TransformationEngine
│       ├── impact/                     # MetadataImpactAnalyzer
│       ├── query/                      # QueryExecutionContext / AiQueryOptimizer(纯逻辑部分)
│       └── model/                      # 归一领域模型(单一来源,消除 model↔metadata 重复)
│           ├── EntityMetadata / FieldMetadata / RelationshipMetadata
│           ├── RuleMetadata / OperationMetadata / Registry
│           └── (移除 engine/metadata/* 重复包)
├── bone-metadata-engine-ports/         # artifactId: bone-metadata-engine-ports
│   └── com.bone.metadata.engine.spi
│       ├── MetadataRepositoryPort       # 读取已发布 meta_* 的契约(替代原 MetadataRepository 接口)
│       └── MetadataPlatformBridge       # 租户/事件/权限端口(保留,接真)
├── bone-metadata-engine-runtime/       # artifactId: bone-metadata-engine-runtime
│   └── com.bone.metadata.engine.adapter
│       ├── SdkMetadataRepository        # 实现 Port,基于 bone-metadata-sdk
│       ├── IamMetadataBridge            # 实现 Bridge,接 IAM/tenant
│       └── InMemoryMetadataRepository   # 保留为测试实现(实现 Port)
├── bone-metadata-engine-starter/       # artifactId: bone-metadata-engine-starter
│   └── com.bone.metadata.engine.autoconfigure
│       └── MetadataEngineAutoConfiguration  # 仅装配,绑定 kernel+adapter
└── (原 bone-metadata-engine-core 演进为依赖上述四模块的聚合/或直接废弃)
```

## 2. 依赖方向(ArchUnit 固化)

```
domain  ←  ports  ←  runtime  ←  starter
  (零依赖)   (仅接口)  (依赖 SDK)  (Spring 装配)
```

- `domain` **禁止** import `org.springframework.*` / `com.bone.metadata.sdk.*` / `com.bone.metadata.engine.adapter.*`。
- `ports` 仅定义接口,可 import `domain.model`。
- `runtime` 可实现 ports,可 import `domain` + `bone-metadata-sdk`。
- `starter` 仅做 `@AutoConfiguration` 装配。

## 3. 端口契约设计

### 3.1 MetadataRepositoryPort

```java
package com.bone.metadata.engine.spi;

/** 读取已发布元数据的端口(由 runtime 基于 SDK 实现) */
public interface MetadataRepositoryPort {
    EntityMetadata findPublishedEntity(String entityCode);
    List<EntityMetadata> listPublishedEntities();
    FieldMetadata findField(String entityCode, String fieldCode);
    // 读侧为主;写侧仍归 sdk/server
}
```

### 3.2 MetadataPlatformBridge(接真)

```java
package com.bone.metadata.engine.spi;

public interface MetadataPlatformBridge {
    String currentTenantId();
    void publishEvent(MetadataChangedEvent event);
    boolean hasPermission(String principal, String permission);
}
```

`IamMetadataBridge` 实现 `currentTenantId()` → `TenantContext`;`publishEvent()` → Spring `ApplicationEventPublisher`;`hasPermission()` → IAM client。

## 4. 与 bone-metadata-sdk 的接口映射(design 阶段核对项)

| engine 需求 | SDK 提供 | 映射方式 |
|------------|---------|---------|
| 读取已发布实体元数据 | `Repository.findByCriteria(Criteria)` / `PageResult` | `SdkMetadataRepository` 构造 Criteria 查 `bone_meta_entity` |
| 多租户隔离 | `TenantContext` / `TenantAbstractEntity` | `IamMetadataBridge.currentTenantId()` 写入 TenantContext |
| 审计字段 | `AbstractEntity`(createdBy/updatedBy) | adapter 填充 |

> ⚠️ design 落地前须实际核对 SDK `Repository` / `SqlBuilder` 方法签名,确认 Port 方法可行(已在 proposal 风险中标注)。

## 5. MetadataEngine 重构为 Application Service

- `MetadataEngine` 由 `@Component` 大聚合 → `com.bone.metadata.engine.application.MetadataEngineService`(或保留名但仅做编排)。
- 依赖:`MetadataRepositoryPort` + `MetadataPlatformBridge` + domain 引擎(构造注入,domain 引擎为普通类)。
- 移除其自管理的 `ConcurrentHashMap` 缓存(改用 `cache/MetadataCacheManager` 经 Port 读后缓存,或 SDK 自带缓存)。

## 6. 消除重复类(具体清单)

| 重复对 | 归一目标 |
|--------|---------|
| `model/MetadataRegistry` ↔ `metadata/MetadataRegistry` | 保留 `domain.model.MetadataRegistry` |
| `model/BusinessRuleMetadata` ↔ `metadata/BusinessRuleMetadata` | 保留 `domain.model.RuleMetadata` |
| `model/EntityMetadata` ↔ `metadata/EntityMetadata` | 保留 `domain.model.EntityMetadata` |
| `model/OperationMetadata` ↔ `metadata/OperationMetadata` | 保留 `domain.model.OperationMetadata` |

迁移策略:domain 包为单一事实来源,metadata/* 下的仅作"已发布元数据 DTO"时另立 `dto/` 包区分,不再与领域模型混用同名。

## 7. 测试策略

- `engine-domain`:纯单测(无 Spring),覆盖表达式/规则/校验/计算算法。
- `engine-runtime`:基于 SDK + H2/MySQL 集成测试,验证 `SdkMetadataRepository` 读已发布元数据。
- `engine` 聚合模块:`ArchitectureTest` 固化四层依赖方向(零框架依赖门禁)。
- `bone-metadata-server`:新增一个集成测试验证"经 engine 读已发布实体元数据"链路。


## 设计复核修正（2026-08-25，业界最佳实践视角）

经端口-适配器/六边形架构/ACL 防腐层/整洁架构复核，确认原方案架构形态合理，并落地以下 3 项修正：

### 修正 1：领域模型范围（T2 归一约束）
`domain/model` 只放**核心元数据概念**（`EntityMetadata`/`FieldMetadata`/`RelationMetadata`）。
AI 自动填充、计算字段、虚拟字段、动态显示等引擎增强属性**暂留 `metadata` 包作为引擎增强视图**，
不塞入 domain 层，避免领域层背负 30+ 展示/AI 字段而污染。T2 归一时不扩大 domain 职责面。

### 修正 2：多租户查询统一封装
`SdkMetadataRepository` 内部封装 `withTenant(Criteria)` 方法，统一注入 `tenant_id` 过滤
（取自 `MetadataPlatformBridge.currentTenantId()`），避免在每个读方法里散落 `Criteria.eq("tenantId", ...)`。
SDK `Repository` 是否自动带 tenant 过滤待 T4 验证；若 SDK 不自动带，则由 `withTenant` 兜底。

### 修正 3：适配器测试用 Mockito（不依赖真实数据源）
`SdkMetadataRepositoryTest` 使用 **Mockito mock `Repository<MetaEntityPo>`**，喂 `MetaEntityPo` 列表，
验证 Criteria 构造与 `MetaEntityConverter` 转换结果。**不依赖 H2/真实数据源**
（engine-core 作为库无 `SqlExecutor`/数据源 bean，真实库测试起不来；真实集成测试放到 T6 server 侧）。
