# Comet Design Handoff

- Change: refactor-metadata-engine-boundary-ddd
- Phase: design
- Mode: compact
- Context hash: 3afe067b92532bc6758180eb522baf6c563dd0ff86205f17a08dd42507a34deb

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/refactor-metadata-engine-boundary-ddd/proposal.md

- Source: openspec/changes/refactor-metadata-engine-boundary-ddd/proposal.md
- Lines: 1-93
- SHA256: 0d6f9fe5f086e338096a6dc284b84ffa1f15ea9c78d8300abfbc545d0ade8fcb

[TRUNCATED]

```md
# Proposal: bone-metadata-engine 职责边界重划(DDD)

> Change ID: `refactor/metadata-engine-boundary-ddd`
> Workflow: Comet Classic (full)
> Status: open (待决策点确认)

## 1. 背景与问题陈述

`bone-metadata-engine`(模式 B 运行时元数据面)当前**定位与边界模糊**,实证如下:

| 证据 | 发现 | 结论 |
|------|------|------|
| 全工程 pom 搜 `bone-metadata-engine` | **0 处依赖** | 它是**孤岛**,无任何模块引用 |
| `MetadataRepository` 实现 | 唯一实现 `InMemoryMetadataRepository`(纯内存) | 不接数据库,与持久化脱节 |
| `BoneMetadataEngineApplication` | 有独立 `main`,但 README 说"默认不参与单体启动" | **库 vs 应用身份冲突** |
| 内部重复类 | `model/MetadataRegistry` ↔ `metadata/MetadataRegistry`;`model/BusinessRuleMetadata` ↔ `metadata/BusinessRuleMetadata` | 边界混乱的代码表征 |
| `MetadataEngine` 类 | 单 `@Component` 聚合 管理+注册+缓存+通知 | 违反 domain 纯净 + 分层依赖 |
| `MetadataPlatformBridge` | 仅有 `Noop` 实现 | 端口从未接真平台 |
| 能力广度 | 156 文件:表达式/规则/校验/计算/转换/AI/工作流/查询优化/多租户/缓存/影响分析 | 极全但**无对外 API 契约、无发布出口** |

### 根本原因(三层)

1. **身份未决(库 vs 应用)**:既是可独立启动的 Spring Boot 应用(`@Component` 自扫描 `com.bone`、有 starter),又被期望作为"计算面库"复用 —— 身份冲突直接造成边界不清。
2. **与 `bone-metadata-sdk` 职责重叠**:README 声明"不替代 sdk 做 JDBC",但代码里 `MetadataRepository` 自实现(虽内存)与 SDK 平行,而非"engine 决策 → sdk 执行"的协同。模式 B 闭环从未真正接上。
3. **端口从未接真**:`MetadataPlatformBridge` 默认 Noop,始终未接入 IAM/租户/事件。

## 2. 目标形态(已与用户确认)

**融入单体复用 SDK**:engine 作为**库**,`MetadataRepository` 端口接 `bone-metadata-sdk`(读已发布 `meta_*`),被 `bone-metadata-server` 依赖,模式 B 真正生效。改动中等、价值最高。

## 3. DDD 边界重划方案

```
bone-metadata-engine
├── engine-domain/         【纯领域·零框架依赖】
│   ├── expression/  rule/  calculation/  validation/  transformation/  impact/
│   └── model/  (归一后的 EntityMetadata/RuleMetadata/Registry,消除 model↔metadata 重复)
├── engine-ports/          【端口·仅接口】
│   ├── MetadataRepositoryPort   (读取已发布 meta_* 的契约)
│   └── MetadataPlatformBridge  (租户/事件/权限端口)
├── engine-runtime/        【适配器·复用 SDK】
│   ├── SdkMetadataRepository    (实现 Port,基于 bone-metadata-sdk 读已发布元数据)
│   └── IamMetadataBridge       (实现 Bridge,接真实 IAM/租户)
└── engine-starter/        【装配·仅 Spring Boot auto-config】
```

### 职责划分(遵循 AGENTS.md DDD 规范)

- **Domain Kernel**:表达式引擎、规则引擎、字段计算、校验、转换、影响分析 → 纯算法/策略,**不依赖 Spring、JDBC、SDK**。
- **Ports**:`MetadataRepositoryPort` / `MetadataPlatformBridge` 仅定义接口,实现由 adapter 提供(依赖倒置)。
- **Runtime/Adapter**:`InMemoryMetadataRepository` 保留为测试用;`SdkMetadataRepository` 接 SDK 成为生产实现 → **模式 B 融入单体**。
- **Starter**:`engine-starter` 仅做 Spring Boot 自动装配,把 kernel + 真实 adapter 拼装,供 `bone-metadata-server` 依赖。
- **明确"不负责"**:catalog 管理 API(归 `bone-metadata-server`)、源码生成(归 `studio-generator`/模式 A)、JDBC(归 `bone-metadata-sdk`)。

### 与现有架构冲突的解决

- 当前 `MetadataEngine` 大 `@Component` 违反"domain 纯净 + 依赖方向"。重划后:领域类无注解,`MetadataEngine` 作为 **application service**(有 Spring 生命周期)编排 kernel + ports。
- 消除 `model/*` 与 `metadata/*` 两套 Registry/RuleMetadata 重复 → 单一领域模型。
- 新增 ArchUnit 架构测试,固化"domain 不依赖框架/SDK、adapter 依赖方向正确"的门禁。

## 4. 范围(本次 change 边界)

**In Scope**
- 重划包结构:domain / ports / runtime(adapter) / starter 四层。
- `MetadataRepository` 端口化 + 新增 `SdkMetadataRepository`(基于 SDK)。
- `MetadataPlatformBridge` 接真实 IAM/租户(替换 Noop)。
- 消除 `model/*` ↔ `metadata/*` 重复类,归一领域模型。
- `MetadataEngine` 重构为 application service。
- 新增 ArchUnit 架构测试固化边界。
- `bone-metadata-server` 的 pom 增加对本 engine 的依赖(模式 B 接入)。
- 更新 README / 模块 design 文档。

**Out of Scope**
- 不新增对外 HTTP API(模式 B 经 server 的现有 `/api/v1/metadata` 暴露)。
- 不改动 `bone-metadata-sdk` 内部实现(仅消费其 Repository API)。
- 不引入新的 ORM / 持久化方案(严禁,违反 AGENTS.md §10 强制约束)。
- 不改动模式 A(generator)链路。

## 5. 风险与缓解


```

Full source: openspec/changes/refactor-metadata-engine-boundary-ddd/proposal.md

## openspec/changes/refactor-metadata-engine-boundary-ddd/design.md

- Source: openspec/changes/refactor-metadata-engine-boundary-ddd/design.md
- Lines: 1-135
- SHA256: 9d9de73726ac95d1ea080b585766fc2003f2f6b8f78fac70e8b15a09a6a1055d

[TRUNCATED]

```md
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

```

Full source: openspec/changes/refactor-metadata-engine-boundary-ddd/design.md

## openspec/changes/refactor-metadata-engine-boundary-ddd/tasks.md

- Source: openspec/changes/refactor-metadata-engine-boundary-ddd/tasks.md
- Lines: 1-44
- SHA256: 77cea3cdecd579af94cd4b0e8538147b86135f7858a397103e34ef1f51a7526f

```md
# Tasks: bone-metadata-engine 职责边界重划

> 配合 `proposal.md` / `design.md`。任务按 DDD 四层迁移顺序,每完成一项在 checkbox 标记。

## T1 模块骨架与依赖方向
- [ ] T1.1 新建 Maven 子模块:`engine-domain` / `engine-ports` / `engine-runtime` / `engine-starter`(pom 依赖:domain←ports←runtime←starter)
- [x] T1.2 新增 `EngineArchitectureTest`(增量 ArchUnit 门禁,固化 `spi` 包不依赖 spring/sdk + 端口契约位于 spi 包);domain 全套门禁待 T2 后接入 `BoneDddArchRules`
- [ ] T1.3 迁移 `engine-core` 的 domain 算法类(expression/rule/calculation/validation/transformation/impact/query)到 `engine-domain`,去除 `@Component`/Spring 注解

## T2 领域模型归一(消除重复)
- [ ] T2.1 将 `model/*` 与 `metadata/*` 的 `MetadataRegistry`/`BusinessRuleMetadata`/`EntityMetadata`/`OperationMetadata` 归一为 `engine-domain` 单一来源
- [ ] T2.2 原 `metadata/*` 中"已发布元数据 DTO"另行归入 `engine-domain.dto`,与领域模型区分
- [ ] T2.3 全量编译,消除所有对旧重复类的引用

## T3 端口化(Ports)
- [x] T3.1 定义 `spi.MetadataRepositoryPort`(读侧契约,基于 `repository.MetadataRepository` 读方法子集)
- [x] T3.2 定义 `spi.MetadataPlatformBridge` 端口(tenant/event/loadPublishedEntityJson 三方法)+ `NoopMetadataPlatformBridge` 默认实现

## T4 适配器接入 SDK(Runtime)
- [ ] T4.1 实现 `SdkMetadataRepository`(基于 `bone-metadata-sdk` 的 `Repository.findByCriteria` 读已发布 `meta_*`)
- [ ] T4.2 实现 `IamMetadataBridge`(接 `TenantContext` / Spring event / IAM client)
- [ ] T4.3 保留 `InMemoryMetadataRepository` 作为测试实现(实现 Port)
- [ ] T4.4 核对 SDK API 签名,确认 Port 方法可行(见 design §4)

## T5 MetadataEngine 重构为 Application Service
- [ ] T5.1 将原 `MetadataEngine` `@Component` 大聚合重构为 `application.MetadataEngineService`,仅编排 domain 引擎 + Port
- [ ] T5.2 移除自管 `ConcurrentHashMap` 缓存,改经 Port 读后由 `MetadataCacheManager` 管理

## T6 装配与单体接入
- [ ] T6.1 `engine-starter` 的 `MetadataEngineAutoConfiguration` 仅做装配(kernel + 真实 adapter)
- [ ] T6.2 `bone-metadata-server` 的 pom 增加 `bone-metadata-engine-starter` 依赖(模式 B 接入)
- [ ] T6.3 server 编译通过,engine 作为库被加载

## T7 测试与文档
- [ ] T7.1 `engine-domain` 纯单测(表达式/规则/校验/计算)
- [ ] T7.2 `engine-runtime` 集成测试(`SdkMetadataRepository` 读已发布元数据)
- [ ] T7.3 `bone-metadata-server` 新增"经 engine 读已发布实体"集成测试
- [ ] T7.4 更新 `bone-metadata-engine/README.md`(四层职责边界)
- [ ] T7.5 更新模块 design 文档(`doc/design/modules/9. SmartMeta 引擎模块技术说明.md`)

## T8 门禁与收尾
- [ ] T8.1 全量 `mvn spotless:apply` + ArchUnit 通过
- [ ] T8.2 全量测试(ArchUnit + 引擎单测 + server 集成)通过
- [ ] T8.3 Comet Classic verify → archive

```
