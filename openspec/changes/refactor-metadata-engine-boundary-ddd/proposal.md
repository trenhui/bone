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

| 风险 | 缓解 |
|------|------|
| 重划触及 156 文件,回归面大 | 逐层迁移 + ArchUnit 门禁 + 保留 InMemory 实现供单测 |
| SDK API 契约不匹配 engine 期望 | design 阶段先核对 SDK `Repository`/`SqlBuilder` 接口,定义 Port 映射 |
| 破坏现有(虽为孤岛)引擎行为 | 保留全部现有引擎算法逻辑,仅调整组织与依赖方向 |

## 6. 成功标准

- [ ] engine 四层包结构落地,domain 零框架依赖(ArchUnit 校验通过)。
- [ ] `bone-metadata-server` 成功依赖 engine 并通过编译。
- [ ] `SdkMetadataRepository` 能经 SDK 读取已发布元数据(集成测试通过)。
- [ ] 重复类消除,无编译警告。
- [ ] 全量测试(ArchUnit + 引擎单测 + server 集成)通过。
