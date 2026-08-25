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

## 模块

| 子模块 | artifactId | 说明 |
|--------|------------|------|
| `bone-metadata-engine-core` | `bone-metadata-engine-core` | 引擎实现（`com.bone.metadata.engine`） |
| `bone-metadata-engine-starter` | `bone-metadata-engine-starter` | Spring Boot 自动装配 |

## 包结构与依赖方向（DDD 端口-适配器）

`bone-metadata-engine-core` 内按边界包重划（物理拆分子模块列为后续可选项）：

```
com.bone.metadata.engine
├── spi/        # 端口（ports）：MetadataRepositoryPort / MetadataPlatformBridge，仅接口，零 spring/sdk 依赖
├── adapter/    # 运行时（runtime）：SdkMetadataRepository / IamMetadataBridge / InMemoryMetadataRepositoryPort（防腐层 ACL）
├── architecture/  # ArchUnit 增量门禁
├── metadata/   # 引擎增强视图模型（EntityMetadata / SmartFieldMetadata / WorkflowMetadata 等）
├── model/      # 领域/规则模型
├── repository/ # 既有仓储接口与内存实现（兼容保留）
└── 引擎算法     # expression / rule / calculation / validation / transformation / impact / query
```

依赖方向：`domain（零依赖）← spi（仅接口）← adapter（依赖 SDK/Spring）← starter（Spring 装配）`。

`SdkMetadataRepository` 经 `bone-metadata-sdk` 的 `Repository<MetaEntityPo, Long>` 读取已发布 `meta_entity`/`meta_field`，
经 `MetaEntityConverter`（防腐层 ACL）转换为引擎领域模型；多租户统一经 `withTenantEntity/withTenantField` 注入 `tenant_id` 过滤。
`IamMetadataBridge` 将 `currentTenantId()` 接到 `TenantContext`、`publishEvent()` 接到 Spring `ApplicationEventPublisher`。

## 构建

```bash
mvn -pl bone-engine/bone-metadata-engine -am test
```

默认不参与平台单体启动。设计说明：[9. SmartMeta 引擎模块技术说明.md](../../doc/design/modules/9.%20SmartMeta%20引擎模块技术说明.md)。
