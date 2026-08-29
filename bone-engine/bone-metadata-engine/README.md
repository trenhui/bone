# bone-metadata-engine

**智能元数据引擎 · 模式 B（运行时元数据面）** 的计算面实现：规则、表达式、SmartQL、**按元数据动态数据访问** 等。

> **产品定位（双模式）**见 [元数据能力-实现映射与竞品对照.md](../../doc/design/modules/元数据能力-实现映射与竞品对照.md) §1.3。  
> - **模式 A**：`sdk` + `server` + `studio-generator` → 生成可编译源码。  
> - **模式 B（本模块）**：`sdk` + **engine** → 解释执行，标准 CRUD **无需** 为每实体生成 Controller。

## 职责边界

| 负责 | 不负责 |
|------|--------|
| 读取已发布 `meta_*`，编排动态 CRUD / SmartQL | 替代 `bone-metadata-sdk` 做 JDBC |
| 校验、业务规则、表达式运行时 | 替代 `bone-metadata-server` 的 catalog 管理 API |
| 与 sdk 协同：`engine` 决策 → `sdk` 执行 | 替代 generator 输出完整 DDD 工程（模式 A 仍需要 generator 作逃逸舱） |

## 模块（物理拆分：domain / ports / runtime / starter）

| 子模块 | artifactId | 说明 |
|--------|------------|------|
| `bone-metadata-engine-domain` | `bone-metadata-engine-domain` | 领域模型与纯算法（`com.bone.metadata.engine.domain.*`，零依赖） |
| `bone-metadata-engine-ports` | `bone-metadata-engine-ports` | 端口接口（`com.bone.metadata.engine.ports.*`，仅依赖 domain） |
| `bone-metadata-engine-runtime` | `bone-metadata-engine-runtime` | 运行时实现（`com.bone.metadata.engine.runtime.*`，SDK/Spring 适配器、引擎算法、数据面） |
| `bone-metadata-engine-starter` | `bone-metadata-engine-starter` | Spring Boot 自动装配 |

原 `bone-metadata-engine-core` 已按边界拆分并删除。

## 包结构与依赖方向（DDD 端口-适配器）

```
com.bone.metadata.engine
├── domain/     # 领域模型（model / metadata / core / common / annotation / exception / multi / impact），零 Spring/SDK 依赖
├── ports/      # 端口接口（spi.MetadataRepositoryPort / spi.MetadataPlatformBridge / registry / repository 接口），仅依赖 domain
├── runtime/    # 运行时实现（adapter 防腐层 / cache / security / query / rule / validation / service / repository 实现 / 引擎算法 / 数据面）
└── starter/    # Spring 自动装配（config / autoconfigure / platform）
```

依赖方向：`domain（零依赖）← ports（仅接口，依赖 domain）← runtime（依赖 domain+ports+SDK/Spring）← starter（依赖 runtime）`。

`runtime.adapter.SdkMetadataRepository` 经 `bone-metadata-sdk` 的 `Repository<MetaEntityPo, Long>` 读取已发布 `meta_entity`/`meta_field`，
经 `runtime.adapter.MetaEntityConverter`（防腐层 ACL）转换为引擎领域模型；多租户统一经 `withTenantEntity/withTenantField` 注入 `tenant_id` 过滤。
`runtime.adapter.IamMetadataBridge` 将 `currentTenantId()` 接到 `TenantContext`、`publishEvent()` 接到 Spring `ApplicationEventPublisher`。

## 构建

```bash
mvn -pl bone-engine/bone-metadata-engine -am test
```

默认不参与平台单体启动。设计说明：[9. SmartMeta 引擎模块技术说明.md](../../doc/design/modules/9.%20SmartMeta%20引擎模块技术说明.md)。
