# 采购域 · BONE 框架能力对齐与落地基线

> **版本**：v0.3（2026-11-09；v0.1 为初版，正文 v0.3 修订记录与 SVG 版本戳此前已先行，本轮统一）
> **定位**：采购域**框架能力与落地约束真源（第四真源）**。回答「BONE 到底有什么、怎么接、哪些必须自己建、哪些由机器门禁证明」。领域语义见 [采购领域架构设计方案.md](./采购领域架构设计方案.md)，技术架构见 [采购总体架构方案.md](./采购总体架构方案.md)，平台约束见 `doc/architecture/Bone-DDD-最终实践方案.md` / `Bone-消息与事件规范.md` / `bone-engine/bone-workflow-engine/README.md`（总体 §15.1）。
> **方法**：全部结论经**源码级实证**（类名 / 接口名 / 配置键 / DDL 表名 + `file:line`），并严格区分「源码已实现」「仅规范承诺」「完全不存在」。二手结论（含引擎 README 的状态清单）一律回源码复核，不采信文档自述。
> **业界对标**：Platform Engineering「golden path / paved road」与平台即产品、Policy as Code / guardrails、SAP Clean Core 2025 **A–D 四级扩展成熟度**与 AEM（按耦合度决定 renovate / re-platform）、Outbox + Idempotent Consumer、SAP MDG-S / DAMA-DMBOK。
> **适用读者**：采购域开发、平台组、架构评审。本文的 §3 / §6 是**一期可直接执行的工程约束**，不是背景材料。

---

## 0. 结论摘要：本轮纠正了什么

v0.2 的领域边界、Vendor 分离、Finance 裁决、部署单元映射、对账三通道等设计**经复核成立，予以保留**。本轮只纠正「平台能力声明」与「落地约束」两类问题。

| # | 级别 | v0.2 的前提 / 表述 | 源码实证 | 处置 |
|---|------|--------------------|----------|------|
| 1 | **P0** | 「WF v1 未实现子流程 / 退回指定节点 / 委派 / SLA」，据此设计 5 条一期降级 + 2 项共建 | `bone-workflow-engine/README.md:147` **清单已过时**；`WorkflowEngineImpl.java:182`（reassign）/`:289`（transfer）/`:372`（addSign）/`:648,1530`（rejectToNode）/`:1173`（`case CC`）与 `WorkflowDefinition.java:98`（`CC`）均已落地。真缺口仅 **子流程 / SLA 超时自动转交 / 独立 delegate 语义** | 撤回 4 条无谓降级；改写总体 PA-02、§10、§12.2，领域 §7.2 / §8.1，共建清单重排（§4） |
| 2 | **P0** | 规划 9 个 `bone-procurement-*` 模块，未提仓库已有采购实现 | `bone-apps/bone-app-purchase/` 已存在 12 个主代码类（`PurchaseWorkflowStatusPort.java:37 implements WorkflowStatusPort`、`PurchaseOrderPayHandler.java:39 implements RuntimeActionHandler`），但**无 `@Table` 实体、无 `Repository`、无采购业务 DDL** | 新增 §7：保留为低代码 Runtime 适配层并划清边界，避免两套采购模块并存 |
| 3 | **P1** | 平台能力名称被当作通用能力直接引用 | `RuntimeAccessGuard` 仅属 `bone-metadata-server` 低代码运行时；`QueryPort` 无框架级接口；`@TenantScope` 是 SDK `@Sql` 通道注解；`StructuredConditionEvaluator` 是 domain 缺省实现而非端口；「审计总线 / HMAC 验签 / 统一任务中心」无对应实现 | 新增 §1 术语校准表 + 总体/领域全文改名（§2） |
| 4 | **P1** | 「门禁总入口 = `scripts/check.sh` + CI」「机器可验证 gate 优先」 | `.github/workflows/ci.yml` **零调用** `scripts/check.sh`（`:122-123` 仅在注释提及）；CI 的 blocking 载体是 `ci.yml:27 backend-quality` 约 30 个脚本 + `:66 mvn verify`；`check.sh` 是 59 步本地/pre-commit 入口 | §6 拆分 CI blocking 与本地入口，重列采购域必须过的门禁 |
| 5 | **P1** | 「冻结一期实体 DDL（对齐 metadata-sdk 列基线）」但没给落地约束 | 无「注解 → DDL」生成能力；DDL 真源是手写 `bone-init.sql`；实体漏声明 `tenantId` / `@Deleted` / `@Version` 会分别导致静默跨租户读、物理删、乐观锁失效 | 新增 §3 持久化最小闭环（可直接抄）+ §6.2 实体声明门禁 + 负向探针 |
| 6 | **P2** | 采购文档族无任何机检脚本，总体 §12.2「门禁引用校验」仍为待办 | `procuraAI/` 原仅 `.md/.html/package.json`；synapClaim 族已有 `tools/check_all.py` + 9 个 `_check_*.py` 的成熟范式 | 新增 `tools/check_procura_alignment.py`（§6.3），带 `--self-test` 注入式负向探针 |

> **不改的部分**：PA-04（Finance 裁决）、PA-07（Vendor 闸门）、PA-08（单应用单库）、对账三通道、Clean Core / ACL / AI fail-closed、总体 §8.3 NFR 与 §8.4 双跑割接、领域 §7.5 幂等四层 / §7.6 单条隔离 / §7.8 缓存失效。

---

## 1. BONE 已有能力全景（实证矩阵）

> 路径均相对仓库根；「成熟度」三态：**S** = 源码已实现可直接复用，**D** = 仅规范/设计承诺需自建或共建，**N** = 完全不存在需新建。

### 1.1 持久化与元数据

| 能力 | 真实载体 | 证据 | 态 | 采购域用法 |
|------|----------|------|----|-----------|
| 实体→列映射（注解反射，唯一真源） | `TableMetadataResolver` | `bone-engine/bone-metadata-sdk/src/main/java/com/bone/metadata/sdk/domain/spec/TableMetadataResolver.java:35,50-75,170-259` | S | 聚合根 `@Table` + 字段蛇形映射；`@Column` **仅覆盖列名/约束，不是驱动开关** |
| 仓储与装配 | `Repository<T,ID>` / `@EnableSqlRepositories` / `RepositoryRegistrar` | `.../sdk/Repository.java:23`、`.../sdk/domain/annotation/EnableSqlRepositories.java:13`、`.../sdk/sql/proxy/RepositoryRegistrar.java:29` | S | 每聚合一个 `XxxRepository extends Repository<T,Long>`；启动类 `basePackages` 必须覆盖 |
| 乐观锁 `@Version` | `DynamicUpdateBuilder` / `BaseRepository` | `.../sdk/query/builder/DynamicUpdateBuilder.java:68-77`、`.../sdk/BaseRepository.java:273-300` | S | 每个聚合显式 `@Version private Long version`；DDL 必须含 `version` 列 |
| 租户注入与过滤 | `TenantAbstractEntity` / `TenantFilterInjector` / `TenantInterceptor` | `bone-framework/bone-core/.../tenant/TenantAbstractEntity.java:19-23`、`.../sdk/query/builder/TenantFilterInjector.java:32-35`、`bone-framework/bone-web/.../TenantInterceptor.java:17` | S | 实体继承 `TenantAbstractEntity`；值由 SDK 从可信 `TenantContext` 注入，业务层禁直读 |
| 软删 `@Deleted` | `AbstractEntity` / `BaseRepository` | `.../core/domain/entity/AbstractEntity.java:37-39`、`.../sdk/BaseRepository.java:454-459` | S | 继承基类即带；**`@Sql` 通道不自动注入**，需显式条件 |
| EAV / 扩展字段 | `ExtensionMode` / `ExtensionCoordinator` / `EavHandler` | `.../sdk/domain/enums/ExtensionMode.java:4-8`、`.../sdk/extension/ExtensionCoordinator.java:21`、`.../sdk/extension/handler/EavHandler.java:13` | S | 仅长尾扩展属性；核心/事务/高频字段必须物理列（CORE-09、DDD `:1438`） |
| YAML / MyBatis 风格 SQL 模板 | `YamlTemplateParser` / `MyBatisTemplateParser` | `.../sdk/sql/template/parser/YamlTemplateParser.java:17`、同目录 `MyBatisTemplateParser.java` | S | 动态查询模板，**与引入 MyBatis 框架无关**（`scripts/check-orm-banned.py:27-30`） |
| 手写 DDL + 列级对账 | `bone-init.sql` / `check-sql-entity-column-coverage.py` / `EntityTableDdlCoverageTest` | `bone-init.sql`、`scripts/check-sql-entity-column-coverage.py:27-31`、`bone-apps/synap-claim/.../EntityTableDdlCoverageTest.java:19-41,92-97` | S | 一期新增表手写进 `bone-init.sql` + 补列级覆盖测试 |
| catalog 模型 → DDL 非破坏性对齐 | `JdbcPhysicalStructureGatewayAdapter` | `bone-engine/bone-metadata-server/.../catalog/infrastructure/gateway/JdbcPhysicalStructureGatewayAdapter.java:21-30,467` | S | 低代码 Runtime 实体通道，与 Java 注解实体**不是同一真源**，不得混用 |
| 注解自动生成建表语句 | — | 全仓未找到 `DdlGenerator` / `generateDdl` | **N** | 禁止在方案里承诺「由实体生成 DDL」 |

### 1.2 工作流引擎

| 能力 | 真实载体 | 证据 | 态 |
|------|----------|------|----|
| 两制品 + 7 必需端口 + 1 可选 | `bone-workflow-core` / `bone-workflow-spring-boot-starter` | `bone-engine/bone-workflow-engine/README.md:24-27`；7 端口 `.../ports/spi/*.java`；`WorkflowEngineAutoConfiguration.java:233-255,308-317` | S |
| 四条硬纪律 + 冻结 + fail-closed + 终态 | `WorkflowEngineImpl` | `:593-599`（终态）、`:619-625`（任务 CAS）、`:663-675`（原子推进）、`:1352-1380`（挂起）、`:1401-1424`（审批人冻结） | S |
| 改派 / 转办 / 加签 / 退回指定节点 / 抄送 / 终止 / 轨迹 / 恢复 | 同上 | `:182/:289/:372/:648,1530/:1173/:1056/:946/:768`；REST `RuntimeApprovalController.java:104-170` | S |
| 设计器 / 版本回溯 / `wf_*` 实体 | starter + DDL | `WorkflowDesignerController.java:41-180`；`bone-init.sql:4159-4306` | S |
| 子流程 | `NodeType` 仅 `START/APPROVE/CONDITION/CC/END` | `WorkflowDefinition.java:90-101` | **N** |
| SLA 超时提醒 / 自动转交 | 仅 `Clock` 注入与注释 | `README.md:145,147`；`WorkflowInstanceRepository.java:254` | **N** |
| 独立 `delegate`（委派）方法 | 只有 `reassignTask` / `transferTask` / `addSign` | `WorkflowEngineImpl.java:182,289,372` | **N**（语义可由改派近似覆盖） |

### 1.3 事件、集成、安全、前端

| 能力 | 真实载体 | 证据 | 态 |
|------|----------|------|----|
| Outbox 物理表 | `bp_outbox` / `int_outbox` / `wf_status_outbox` / `t_domain_event_outbox` | `bone-init.sql:960,856,4284,3974` | S |
| 消费幂等表 | `bp_processed_event`（`consumer_group + event_id` 唯一键） | `bone-init.sql:994-999`；`blueprint/.../ConsumedEventRecord.java:22` | S |
| 信封 8 字段 | `IntegrationEventEnvelope` / 事件规范 §3 | `bone-platform/bone-integration/.../IntegrationEventEnvelope.java:7-15`；`doc/architecture/Bone-消息与事件规范.md:44-61` | S |
| DLQ 转投 `platform.dead_letter.v1` | **仅 blueprint 中继真正转投** | `blueprint/.../OrderOutboxRelayPortAdapter.java:339-347`；`OrderOutboxProperties.java:43`；`bone-integration/.../IntegrationOutboxRelay.java:70-77` 仅 `markFailed()` | S / **D** |
| 通用 Outbox starter / `@DomainEvent` 注解 | 只有 `DomainEvent` 空接口 + `DomainEventPublisher` 端口 + `NoDomainEvent` 注解，各模块自建 publisher | `bone-core/.../DomainEvent.java:4`、`DomainEventPublisher.java:8,24`、`annotation/NoDomainEvent.java:15` | **N** |
| Webhook HMAC 验签 | 规范有 `X-Bone-Signature` 但 checklist 未完成 | `Bone-消息与事件规范.md:138,158`；`bone-integration` 内 Grep 零命中；`WebhookNotificationChannel.java:53-58` 无签名 | **D** |
| integration HTTP 连接器 / 凭据加密 | `FlowNodeExecutor` / `ConnectorType` / `ConnectorSecretSupport` | `bone-integration/.../FlowNodeExecutor.java:24,30`、`ConnectorType.java:5-25` | S |
| 业务侧动作级权限 | **Spring Security `@PreAuthorize("hasAuthority('...')")`** + `PlatformAccessGuard` | `bone-blueprint/.../OrderController.java:71`；`bone-iam/.../PlatformAccessGuard.java:20`；`DefaultPermissionCodes.java:6` | S |
| 低代码运行时字段级授权 | `RuntimeAccessGuard` / `RuntimeFieldVisibilityProjector` | `bone-metadata-server/.../runtime/application/RuntimeAccessGuard.java:35`、`RuntimeFieldVisibilityProjector.java:29` | S（**仅限 Runtime 数据面**） |
| 审计 | IAM 内 `AuditLog` + `iam_audit_log` + `WorkflowAuditPort`；**无审计总线 / 统一 schema** | `bone-iam/.../AuditLog.java:17`；`bone-init.sql:162`；`WorkflowAuditPort.java:12` | S / **N** |
| 统一任务中心 / `TaskCenterPort` | 只有 workflow 自身待办 `listTasksByAssignee` 与审批中心 REST | `WorkflowInstanceRepository.java:203`；`RuntimeApprovalController.java:32,39,91` | **D** |
| 多租户全链路 | `TenantContext` / `TenantInterceptor` / `@TenantScope` / `TenantSqlRewriter` | `bone-core/.../TenantContext.java:17`；`@TenantScope` `.../sdk/domain/annotation/TenantScope.java:25-27,34` | S |
| 统一响应 / 错误码 | `ApiResponse<T>` / `PageResult<T>` / `{Module}Errors` + `ErrorCode` | `bone-core/.../ApiResponse.java:18`、`PageResult.java:13`；`synap-claim/.../ClaimErrors.java:14` | S |
| 前端 | `@bone/ui` 设计系统壳；表格在 `@bone/ui-schema`；审批轨迹在 `bone-metadata-app` | `bone-frontend/packages/ui/package.json:2`；`ui-schema/src/SchemaRenderer.tsx:83`；`bone-metadata-app/.../ApprovalProgress.tsx:2` | S（部分） |
| 代码生成器 | `studio-generator` 16 个模板全为 Java/Markdown，**无前端页面模板、无采购专用模板** | `bone-engine/studio-generator/src/main/resources/templates/*.ftl`；`EntityGenerator.java:16` | S（后端骨架） |
| B2B 供应商门户 | 仅 `UserType.EXTERNAL_USER` 枚举 | `bone-security/.../UserType.java:29-33` | **N** |

### 1.4 术语校准（v0.2 用词 → 正确用词）

| v0.2 写法 | 问题 | 正确写法 |
|-----------|------|----------|
| 「动作级细码授权（`RuntimeAccessGuard`）」 | 该类只服务低代码 Runtime 的实体/字段授权 | 业务接口用 `@PreAuthorize("hasAuthority('procurement:po:write')")`；Runtime 数据面才用 `RuntimeAccessGuard` |
| 「读经 `QueryPort` 投影」 | 无框架级 `QueryPort` 接口，是业务侧端口命名约定（CORE-05 规范用语） | 「按 CORE-05 定义业务侧 `*QueryPort` 读端口」，样例 `synap-claim/.../PolicyQueryPort.java:11` |
| 「多租户 `@TenantScope`」 | 它是 SDK `@Sql` 通道必声明注解，漏声明启动期拒绝注册 | 租户隔离 = `TenantContext` + `TenantInterceptor` + 实体继承 `TenantAbstractEntity`；`@TenantScope` 仅用于 `@Sql` 方法 |
| 「7 必需端口含 `StructuredConditionEvaluator`」 | 它是 domain 缺省实现，不是端口 | 可替换端口是 `WorkflowConditionEvaluator.java:16`；7 端口见 §1.2 |
| 「`listTasksByAssignee` 是端口」 | 是 `WorkflowInstanceRepository` 的方法 | 端口 = `WorkflowInstanceRepository`；待办方法 = `listTasksByAssignee` |
| 「平台审计总线（ADR-0007/0009）」（**已废弃写法**） | 无总线实现 | 「IAM `iam_audit_log` + 引擎 `WorkflowAuditPort`；统一审计 schema 为共建项」 |
| 「`bp_processed_event` 幂等」 | `bp_` 前缀属 blueprint 上下文 | 采购域建 `t_proc_processed_event`（同 `consumer_group + event_id` 唯一键）或经平台确认共用，不得默默跨上下文写表 |

---

## 2. 三类清单与采购域责任分派（对标 Clean Core Level A–D / golden path）

> SAP 2025 Clean Core 用 A–D 四级评估扩展的**解耦度与升级安全**：Level A = 仅用 released API 的 side-by-side / ABAP Cloud；Level C/D = 访问内部对象或改写核心表（应避免）。AEM 要求「按耦合度决定 renovate 还是 re-platform」，而非一律外置。Platform Engineering 的 golden path 要求把复用能力做成**带 CI / 安全 / 可观测默认值的模板**，并留 escape hatch。据此把采购域能力分三轨：

| 轨道 | 判据 | 采购域动作 | Clean Core 类比 |
|------|------|-----------|-----------------|
| **① 直接复用（paved road）** | 源码已实现且有 CI 门禁守护 | 只用其公开端口/注解，不改平台核心；接入方式写进采购域模板仓库 | Level A |
| **② 共建（平台即产品）** | 规范承诺但无实现，或引擎真缺口，且 ≥2 个域需要 | 采购域出需求规格 + 负向验收用例，平台组实现，**以 PR 回灌平台能力**，不建第二套 | Level A/B（经 released API 扩展） |
| **③ 采购域自建** | 业务专属或完全不存在，且不应侵入平台 | 只建业务模块内资产（实体/DDL/ACL/门户/中继/门禁脚本），经 ① 的端口接入 | Level A（side-by-side） |

| 能力 | 轨道 | 依据 | 交付物 |
|------|------|------|--------|
| metadata-sdk 实体/仓储/乐观锁/租户/软删 | ① | §1.1 | 一期实体与 DDL（§3.4） |
| WF 主干能力（含退回/转办/加签/抄送/改派/挂起/恢复/设计器） | ① | §1.2 | 流程定义 + `WorkflowStatusPort` 适配器（复用 `bone-app-purchase` 模式，§7） |
| `@PreAuthorize` 细码 / `ApiResponse<T>` / `{Module}Errors` | ① | §1.3 | 采购域权限码表与错误码常量 |
| integration HTTP 连接器 / 凭据加密 | ① | §1.3 | ERP/Finance/Inventory ACL 适配器 |
| 事件信封 / Outbox 表 / 幂等唯一键模式 | ① | §1.3 | `t_proc_outbox` + `t_proc_processed_event` + 自建中继（§5.1–§5.3） |
| Webhook HMAC 验签 | ③（短期）→② | 规范 §8 未落地 | 采购域 ACL 内自建验签 + 负向探针；成熟后回灌 integration |
| DLQ 转投与重放控制台 | ③（短期）→② | integration 仅 `markFailed()` | 复用 blueprint `OrderOutboxRelayPortAdapter` 模式自建；平台侧控制台列为共建 |
| 通用 Outbox starter | ② | 无统一制品、各模块自建 | 平台组产出 starter，采购域作为首个消费方（golden path 试点） |
| 子流程节点 / SLA 超时自动转交 | ② | 引擎真缺口 | WF v2 共建（§4.4） |
| 跨领域统一任务中心 / 审计事件 schema | ② | 无实现 | 保留 v0.2 共建方向，收窄范围（§4.4） |
| B2B 供应商门户 / 前端页面 | ③ | generator 无前端模板 | 新建 `supplier-portal` app + BFF（总体 §8.1） |
| 采购域实体 / DDL / 门禁脚本 | ③ | 平台不含业务模型 | §3、§6.3 |

> **纪律**：任何「复用」声明必须指向本文 §1 的 S 态证据；未列入 S 态的能力一律按 ②/③ 排期，**不得写进 WBS 的「已复用」栏**。

---

## 3. 持久化最小闭环（一期可直接抄）

### 3.1 注解与字段责任表（决定上线会不会出事故）

| 项 | 责任 | 漏了会怎样 | 证据 |
|----|------|-----------|------|
| `@Table` | **必须显式** | 解析期抛 `MetadataException` | `TableMetadataResolver.java:61-64` |
| `@Id` / `@Version` | **必须显式** | 无版本则无乐观锁；DDL 无 `version` 列则 UPDATE 直接失败 | `check-id-annotation.py:2-9`；`EntityTableDdlCoverageTest.java:92-97` |
| `tenantId`（继承 `TenantAbstractEntity`） | **必须显式继承** | `TenantFilterInjector.inject()` 直接 return ⇒ **静默跨租户读** | `check-tenant-entity-declaration.py:6-10,36-39` |
| `@Deleted`（基类自带） | **必须保留** | `deleteById` 走物理 `DELETE FROM` | `check-soft-delete-declaration.py:2-17` |
| `created_by` / `updated_by` | **应用层显式赋值** | SDK 无 JPA Auditing 等价物，DDL 为 `DEFAULT NULL`，不会自动填 | `bone-init.sql:61-62`；`Bone-Metadata-SDK-最佳实践方案.md:459-464` |
| `created_at` / `updated_at` | DDL `DEFAULT` + `ON UPDATE` | SDK 仍按实体值显式 `SET`，null 会覆盖 | `bone-init.sql:63-64`；`DynamicUpdateBuilder.java:31-42` |
| `version` 值 / `tenant_id` 值 / `deleted` 值 | **SDK 自动**（前提是已声明） | — | `BatchInsertBuilder.java:57-71`、`BaseRepository.java:273-300,454-459` |
| `@Column` | **可选**（覆盖列名/nullable/unique） | 不写时字段按蛇形自动成列 | `TableMetadataResolver.java:255-259` |
| `@TenantScope`（仅 `@Sql` 通道） | **必须显式声明** | `RepositoryFactoryBean` 启动期拒绝注册 | `TenantScope.java:25-27`；`RepositoryFactoryBean.java:208,340` |

### 3.2 DDL 双真源与列级对账

- **不存在「注解 → DDL」生成器**。DDL 唯一真源 = 手写 `bone-init.sql`（`scripts/check-ddl-doc-sync.py:2`）；新增表须人工写入并同步 `doc/architecture/数据库开发规范.md` §2 清单。
- **写侧自动带列、读侧手写 SQL 模板漏列**会导致字段恒 null、再被 `save` 抹掉（`check-sql-entity-column-coverage.py:9-13`）。因此每个 `@Sql` 查询模板必须覆盖实体全部显式 `@Column`（同脚本 `:27-31`），并补 `SqlTemplateEntityColumnCoverageTest` 式的**强制 Java 门禁**（`synap-claim/.../SqlTemplateEntityColumnCoverageTest.java:22-45`）。
- 新增表必备列由 HC-008 守护：`tenant_id / created_at / updated_at / deleted`（`check-ddl-required-columns.py:44`）；另需 `version`（乐观锁）、`created_by / updated_by`。

### 3.3 最小闭环骨架（参照 `synap-claim` 的 `FeatureToggle` 实证闭环）

```
// 1) 实体：bone-apps/<procurement>/.../domain/model/Vendor.java
@Table("t_proc_vendor")
public class Vendor extends TenantAbstractEntity<Long> {
    @Id private Long id;
    @Version private Long version;                    // 必须；DDL 同步 version 列
    @Column(name = "supplier_id", nullable = false) private Long supplierId;
    @Column(name = "legal_entity_id", nullable = false) private Long legalEntityId;
    @Column(name = "vendor_status") private String vendorStatus;
    // createdBy/updatedBy 由 ApplicationService 从登录上下文显式赋值
}

// 2) 仓储：domain/repository/VendorRepository.java
@TenantScope(TenantScopeMode.AUTO)                    // @Sql 通道必声明；纯 Criteria 可省
public interface VendorRepository extends Repository<Vendor, Long> { }

// 3) 启动装配：@EnableSqlRepositories(basePackages = "...domain.repository")
//    参照 ClaimApplication.java:28-33（并 @Import MetadataAutoConfiguration / SqlRepositoryAutoConfiguration）

// 4) ApplicationService：save() 必须配 publishFrom() 或类级 @NoDomainEvent + JavaDoc 理由
//    BoneDddArchRules.java:1245-1298（E-5.4 实测规则，≥10 个模块已 @ArchTest 启用）

// 5) Controller：@PreAuthorize("hasAuthority('procurement:vendor:write')") + ApiResponse<T>
//    错误码：ProcurementErrors.of(...) 形态，参照 ClaimErrors.java:14
```

> 可复制样例：`synap-claim/.../domain/model/config/FeatureToggle.java:22-72` + `FeatureToggleRepository.java:14-24`；更大聚合参照 `ClaimCase.java:39-155` + `ClaimCaseApplicationService.java:51`；供应链实体参照 `bone-blueprint/.../ReplenishmentOrder.java:25-47`（含 `supplierCode`）。

### 3.4 一期实体与必备列清单

| 实体（表） | 对应聚合 | 必备列（除 HC-008 四列 + `version` + `created_by/updated_by`） | 唯一键 |
|-----------|----------|---------------------------------------------------------------|--------|
| `t_proc_supplier` | Supplier | `supplier_code, lifecycle_status, supplier_version` | `(tenant_id, supplier_code)` |
| `t_proc_vendor` | Vendor | `supplier_id, legal_entity_id, company_code, payment_terms, bank_account_ref, payment_blocked, duplicate_invoice_check, vendor_status` | `(tenant_id, supplier_id, legal_entity_id)` |
| `t_proc_vendor_activation` | VendorActivation | `supplier_id, legal_entity_id, gate_result, workflow_instance_id` | 同左幂等键 |
| `t_proc_legal_entity` | Legal Entity | `unified_credit_code, company_code, tax_region, currency` | `(tenant_id, unified_credit_code)` |
| `t_proc_contract` | Contract | `contract_no, review_id, review_snapshot_ref` | `(tenant_id, contract_no)` |
| `t_proc_po` / `t_proc_po_line` | PurchaseOrder（同聚合） | `po_no, po_version, vendor_id, legal_entity_id, price_snapshot_ref` / `line_no, qty, net_price` | `(tenant_id, po_no, po_version)` |
| `t_proc_price_condition` | PurchaseCost 随根 | `vendor_id, item_id, org_id, valid_from, version` | 有效期互斥条件更新 |

> **不变量落点**：PO 与 PO Line 同聚合（强一致：金额 = Σ 行金额）；子实体随根落盘用 `@Cascade` + `@Transient`（CORE-11，DDD `:72`），不得提升为独立聚合。

### 3.5 负向探针（一期上线前必须逐条转红）

| # | 注入 | 期望 |
|---|------|------|
| N1 | 实体去掉 `TenantAbstractEntity` 继承 | `check-tenant-entity-declaration.py` 转红 |
| N2 | 实体去掉 `@Deleted` | `check-soft-delete-declaration.py` 转红 |
| N3 | DDL 去掉 `version` 列但保留 `@Version` | `EntityTableDdlCoverageTest` 转红 |
| N4 | `ApplicationService.save()` 不配 `publishFrom()` 且无 `@NoDomainEvent` | ArchUnit `applicationSaveMustPairWithPublishOrExempt` 转红 |
| N5 | `@Sql` 方法不声明 `@TenantScope` | 启动期注册失败 |
| N6 | 手写 SQL 模板漏一列 | `check-sql-entity-column-coverage.py` 转红 |

---

## 4. 工作流能力校准（P0 修正）

### 4.1 README v1 清单 vs 源码逐条对照

| README:147 声称「未实现」 | 源码实证 | 真实状态 |
|---------------------------|----------|----------|
| 转办 | `WorkflowEngineImpl.java:289-335`、REST `/tasks/{id}/transfer` | **已实现** |
| 加签 | `:372-403`、REST `/tasks/{id}/addSign` | **已实现** |
| 委派 | `reassignTask :182-213`（管理员改派）、`transferTask`（本人转办）；独立 `delegate` 未找到 | **改派已实现 / 独立委派语义缺失** |
| 抄送节点 | `WorkflowDefinition.java:98 CC`、`WorkflowEngineImpl.java:1173-1190` | **已实现** |
| 退回指定节点 | `:648,1530` `rejectToNode`、`:1489-1560` 目标校验 | **已实现** |
| 流程版本回溯 / 可视化设计器 / `wf_*` 实体 | `WorkflowDesignerController.java:159,167`；`bone-init.sql:4159-4306` | **已实现** |
| 终止 / 实例轨迹 / 恢复 | `:1056` / `:946` / `:768` | **已实现** |
| **子流程** | `NodeType` 仅 5 种，全模块无 `subProcess` | **真缺口** |
| **SLA 超时提醒与自动转交** | 仅 `Clock` 注入与注释 | **真缺口** |

> 引擎 README 的状态清单是**文档漂移**（`doc/design/modules/12. 工作流引擎设计.md:651` 亦记录 2026-10-27 已落地 P1-3）。本轮以源码为准；已向平台组登记 README 修订事项（§9）。

### 4.2 一期降级方案（由 5 条收缩为 2 条）

| 真缺口 | 一期处置 | 共建归属 |
|--------|----------|----------|
| 子流程 | CAPA 整改闭环用**串行节点 + 显式阶段字段**模拟；不得伪造 `SUB_PROCESS` 节点类型 | §4.4 ① |
| SLA 超时提醒 / 自动转交 | 日终巡检 + 通知催办；超时不自动改派，人工经**已实现的 `reassignTask`** 干预 | §4.4 ② |
| 独立 `delegate` 语义 | 用 `reassignTask`（管理员改派）或 `transferTask`（本人转办）表达，不新建第二套审批入口 | 不共建，纳入平台术语说明 |

> **撤回 v0.2 的 4 条降级**：「驳回重提走新建修订版本」「委派/转交人工兜底」「抄送走平台通知」「退回节点不可用」——前三者在源码中已有正规能力，第四项 `rejectToNode` 已实现且带目标合法性校验。

### 4.3 采购场景 → 引擎能力映射（更新版）

| 场景 | 直接复用的能力 | 证据 |
|------|----------------|------|
| 供应商准入 / Vendor 开通 / 信息变更 | 流程定义 + `ApproverResolver` + `WorkflowStatusPort` + 重复发起幂等 | `WorkflowEngineAutoConfiguration.java:248-255`；`WorkflowEngineImpl.java:464-475` |
| 合同用印 | `SINGLE/ALL/ANY/RATIO` 会签；`CC` 抄送法务 | `WorkflowDefinition.java:104,98` |
| PO / 价格审批 | `StructuredConditionEvaluator`（缺省实现）+ fail-closed 分支 | `StructuredConditionEvaluator.java:27`；`WorkflowEngineImpl.java:604-608` |
| 驳回重审 | **`rejectToNode`（退回指定节点 + 目标校验）** | `:648,1489-1560` |
| 整改 CAPA | 串行节点模拟子流程；超时人工 `reassignTask` | §4.2 |
| 待办 / 已办 | `listTasksByAssignee` + `WorkflowInboxQuery` | `WorkflowInstanceRepository.java:203`；`WorkflowInboxQuery.java:18` |

### 4.4 共建清单（由 3 项重排，全部要求负向验收）

| # | 共建资产 | 动机 | 采购域贡献 | 验收门禁 |
|---|----------|------|-----------|----------|
| ① | WF v2 **子流程节点类型** | `NodeType` 只有 5 种，CAPA 需真嵌套 | 需求规格 + CAPA 验收用例（负向：子流程终态不得回写父实例） | v1 定义回归全绿 + CAPA 用例通过 |
| ② | **SLA 超时 + 跨领域统一待办接入契约** | 无运行期 SLA；`TaskCenterPort` / 待办 schema 不存在 | 待办聚合 schema（来源域/单据类型/动作码/紧急度/超时策略）+ 接入规范 | ≥1 个其他域按契约接入；注入超时用例必触发 |
| ③ | **统一审计事件 schema 与不可篡改留痕** | 现只有 IAM `iam_audit_log` + `WorkflowAuditPort`，无总线 | 采购域事件字段规范（含 `review_id` / `trace_id` / `legal_entity_id`） | 审计回溯演练 + schema 兼容回归 |

> Owner 与排期沿用领域 §8.1 / 总体 §12；**①② 的需求规格与负向用例在一期冻结**，实现排三期。不自建第二套引擎 / 任务中心 / 审计总线。

---

## 5. 事件、集成与安全的落地约束

### 5.1 Outbox 与幂等表

- 采购域建 `t_proc_outbox`（列对齐 `bp_outbox`：`event_id` 唯一、`topic`、`partition_key`、`envelope_json`、`schema_version`、`status`；`bone-init.sql:960-979`）与 `t_proc_processed_event`（`(consumer_group, event_id)` 唯一键；`:994-999`）。**不直接写 `bp_processed_event`**（跨上下文写他域表）。
- 信封 8 字段 `eventId/eventType/topic/occurredAt/tenantId/traceId/schemaVersion/payload`（`IntegrationEventEnvelope.java:7-15`）；**分区键不在信封内**，落在 outbox 表 `partition_key` 列（默认 `tenant_id`，`bone-init.sql:966`）。
- 顺序敏感链路（PO→Ack→ASN→GR→Invoice→Payment）同一 `partition_key`（事件规范 `:36`）。

### 5.2 中继与 DLQ

- 平台**没有通用 Outbox starter**；采购域需自建 `ProcurementOutboxRelay`，形态参照 `OrderOutboxRelayPortAdapter.java:307,339-347`（重试超限转投 `platform.dead_letter.v1`）。
- **不得照抄 `IntegrationOutboxRelay.java:70-77`**：它超限只 `markFailed()` 不转投；`int_dead_letter` 亦「有表无完整 REST」（`doc/_generated/integration/backlog.md:9`）。采购域 DLQ 重放需自建最小控制台或列入共建。
- 重放须新 `eventId` 或标 `replayOf`（事件规范 §4–§6）。

### 5.3 Webhook HMAC（规范承诺 ≠ 实现）

- `Bone-消息与事件规范.md:138` 定义 `X-Bone-Signature: HMAC-SHA256(body, secret)`，但 `:158` 的 checklist 项「Webhook 有 HMAC 验签」**未完成**；`bone-integration` 内无验签实现（`WebhookNotificationChannel.java:53-58` 请求头无签名）。平台仅有的 HMAC 代码用途不同（钉钉告警 `DingTalkAlertChannel.java:118-119`、blueprint 支付回调 `MockPaymentSignaturePortAdapter.java:96-97`）。
- **采购域 ACL 自建**：常量时间比较、时间戳 + nonce 防重放、密钥轮换、`tenant_id` 绑定；负向探针：缺签名 / 错签名 / 过期时间戳 / 重放 nonce 四类注入必须全部拒绝且记录审计。

### 5.4 权限与可见性

- 业务接口用 `@PreAuthorize("hasAuthority('procurement:<resource>:<action>')")`（`OrderController.java:71` 形态）；权限码表进 `bone-frontend/packages/shared-types/src/bonePermissionCodes.ts`。
- 字段级可见性（供应商银行账号、PII、法人维度）在**低代码 Runtime 通道**可用 `RuntimeFieldVisibilityProjector.java:29`；Java 聚合通道需采购域自行按 §9.1 可见性矩阵裁剪，不得虚报平台能力。
- `RuntimeAccessGuard` 只用于 Runtime 数据面（`WILDCARD_READ/WILDCARD_WRITE/PLATFORM_ADMIN`，`:38-43`）。

### 5.5 前端与门户

- 内部页面：`@bone/ui` 提供主题与设计系统壳；表格用 `@bone/ui-schema` 的 `SchemaRenderer`；审批轨迹参照 `bone-metadata-app/.../ApprovalProgress.tsx` 抽为采购域共享组件（PR 回灌 `@bone/ui` 属共建候选）。
- 门户 `supplier-portal`：**完全新建**（无 app、无 BFF、无外部用户体系，只有 `UserType.EXTERNAL_USER` 枚举）；`studio-generator` 的 16 个模板全为后端 Java/Markdown，**不能生成前端页面**。

---

## 6. 门禁落地（Policy as Code）

### 6.1 CI blocking 与本地入口分离

| 入口 | 真实载体 | 性质 | 对采购域的含义 |
|------|----------|------|----------------|
| **CI blocking** | `.github/workflows/ci.yml:27 backend-quality`（约 30 个 `(blocking)` 脚本）、`:66 mvn clean verify`（ArchUnit）、`:130-131` HC-008、`:136-137` HC-003、`:144-145` HC-006、`:488-534 frontend-quality` | 机器强制，推前必绿 | 采购域代码必须满足这些；**这是 ship-ready 判据** |
| 本地 59 步 | `scripts/check.sh`（`[n/59]`，`:152-161` HC-001 扫 ORM import/pom） | pre-commit / 人工入口，**CI 零调用**（`ci.yml:122-123` 仅注释） | 不能把「check.sh 绿」当作 CI 绿；本机 `.git/hooks/pre-commit` 亦未接线 |
| 文档族门禁 | `synap-claim-docs.yml:43-46` `check_all.py` | CI blocking（仅理赔族） | 采购族按同模式新增，见 §6.3 |

> v0.2 总体 §13「门禁总入口 `scripts/check.sh` + CI」把两者并列会误导验收；v0.3 已改为「CI blocking 为权威，本地入口为辅助」。

### 6.2 采购域必须满足的既有机检

| 门禁 | 判据 | 载体 |
|------|------|------|
| HC-002 分层依赖 | domain 不依赖外层 | `BoneDddArchRules.java:142-160` + `ci.yml:66` |
| HC-003 统一响应 | Controller 返回 `ApiResponse`/`PageResult` | `check-controller-response-envelope.py`（`ci.yml:136-137`） |
| HC-006 持久化唯一 | 无 `JdbcTemplate`/`java.sql.Connection` 等直连 | `check-sdk-persistence.py`（`ci.yml:144-145`） |
| HC-008 新增表必备列 | `tenant_id/created_at/updated_at/deleted` | `check-ddl-required-columns.py`（`ci.yml:130-131`） |
| HC-001 禁 ORM | **Manual**（无 CI 级 `bannedDependencies`、无共享 ArchUnit 规则） | `scripts/check-orm-banned.py` + `check.sh:152-161`；需人工复核并在 CI 补齐前登记 |
| E-5.4 落地语义 | `save()` 必配 `publishFrom()` 或 `@NoDomainEvent` + JavaDoc（**不是禁止 save**） | `BoneDddArchRules.java:1245-1298`，≥10 模块已启用 |
| 实体声明族 | `tenantId` / `@Deleted` / `@Id` 与 DDL 对账 | `check-tenant-entity-declaration.py`、`check-soft-delete-declaration.py`、`check-id-annotation.py` |
| 列级对账 | SQL 模板覆盖实体全部显式 `@Column` | `check-sql-entity-column-coverage.py`（`ci.yml:334`） |
| 前端 | `typecheck / lint / vitest / coverage / build` | `ci.yml:508-534`；**本地 eslint 默认 stylish formatter 会假绿（退出码 0）⇒ 必须 `-f json` 解析 `errorCount`** |

### 6.3 采购域文档族门禁脚本（本轮新增）

`tools/check_procura_alignment.py`（纯 stdlib、零 DB/零 JDK/零网络，仿 `synapClaim/tools/check_all.py` 范式）：

| 判据 | 内容 |
|------|------|
| A1 | **三份域内真源 + 一份治理真源**（计数口径见 `文档资产登记与真源裁决.md §4.1`）版本与 README 登记一致、互相引用存在；**7 份 LLD 卷的 v0.3 / Proposal 状态头与 README 登记** |
| A2 | 跨文档 `§` 引用可解析（支持 `##/###/####`、「4. 标题」点号写法与「总纲 §x」别名），章节编号无重复 |
| A3 | 相对链接与 `assets/` 资产存在（**按各文档自身目录解析**，LLD 卷在 `detailed-design/`），SVG 可解析且不溢出 viewBox |
| A4 | **废弃误判表述黑名单**（v1 缺口清单过时说法、`RuntimeAccessGuard` 动作级授权、审计总线已存在、`check.sh` 为 CI 门禁等）不得作为决策出现 |
| A5 | **必备平台实证锚点**存在（`@PreAuthorize`、`publishFrom`、`TenantAbstractEntity`、`bone-app-purchase`、`ci.yml`、真缺口两项） |
| A6 | 空集合 / 缺文件 fail-closed（防恒绿；含 LLD 卷集合、判据常量、契约工件集合与下界常量） |
| **A7** | **LLD 族 7 卷 12 节模板、必备锚点、L 级待决项与 10 模块覆盖矩阵** |
| **A8** | **契约工件一致性**：① 附录 B 事件注册表 ↔ `asyncapi.yaml` 双向零漂移（门禁内复用生成器 `render` 重算，纯进程内可注入）+ 下界 29 条 + topic 命名规范 + channel title 对应；② 附录 A 错误码 ↔ `openapi.yaml` `x-error-catalog` 双向一致、唯一性、下界 43 条、卷内不得自造码；③ 附录 C 权限码 resource 白名单（下界 33）+ action 白名单 + 六卷覆盖表；④ 拦截未登记临时 topic；⑤ OpenAPI/AsyncAPI/生成器/关键链路时序 SVG 必备锚点与各卷 §2·§9 双引用；⑥ 总纲 §14–§22 与附录 A–E 深度章节锚点、README 与三份上游真源的 A8 索引登记 |
| **A9** | **逐卷可执行规格**：6 卷各有 `## 13. 本卷专题深化（v0.3）` 与 13.1–13.8 八子节；每卷 §13.1 含 §23.9 登记的**本卷专属锚点**（防跨卷复制充数）；§13 含容量 / SLO / FMEA / 灰度回滚 / 保留归档 / 读模型 / 测试门槛七类要素标记；§11 至少 2 条 v0.3 探针、§12 引用总纲 §23；总纲 §23 / 附录 F / B12–B17 / L38–L43 与四份索引登记 A9；常量下界写死防删条目恒绿 |
| **A10** | **资产裁决与收口守护**：① README 9 行旧族状态列无空值 + 资产登记 §3.2 同为 9 行、作废文档不得被新族正文引用（登记表本身豁免）；② 全族 md + 全部 SVG `<text>` 的「自动归档」只允许出现在撤销语境、`AuditBus` / `contract_no 主键` / 全覆盖 等废弃词清零；③ D3-v2 存在且 `ContractReviewed` 唯一 payload 与 03 卷一致；④ 附录 B.1 每条 Frozen 事件在总体 §7.1 有行且 topic 与领域 §6.2 一致；⑤ 契约工件 / SVG / 生成器版本戳统一 v0.3；⑥ 一期机制表 14 行状态与触发条件、重试分层 4 行、WBS 人力列；⑦ 7 份新增真源链接可达、模块注册表 11 行、ATAM ≥10 场景（每个 H 场景挂决策 + 负向探针）/ ≥3 权衡点；⑧ 跨域契约 Item/Inventory/Warehouse 三域章节与 OpenAPI 三个只读投影端点；⑨ **A10.9 六卷 §6 端点 ↔ `openapi.yaml` 方法级双向全覆盖**（外部锚点下界 55 行、5 个已登记跨域只读投影端点白名单、未登记端点拦截；闭合诊断 R8 验收判据） |
| `--self-test` | **全量**注入式变异（条数以脚本输出为准；含 LLD 卷版本/链接/章节/覆盖矩阵、注册表删条、channel 未登记、错误码越权、权限码越权、临时 topic、时序图引用缺失、catalog 反向一致性、关键端点缺失，以及新增的 A8 上游冻结锚点、A6 下界常量、A9 七类要素、A10 各分支、A10.9 的「删 LLD 行 / 删工件端点 / 注入未登记端点」三类反证），逐条证明能转红；**转红判定同时校验失败消息包含预期 key**，防止异常分支假转红 |

运行：`python3 tools/check_procura_alignment.py`（全套）/ `python3 tools/check_procura_alignment.py --self-test`（负向反证）；退出码 `0/1`，输出逐判据 PASS/FAIL。

### 6.4 一期 exit criteria 增补

在总体 §12.2 六项之外新增：

- [ ] §3.5 六条负向探针逐条转红并留痕（含 `save()/publishFrom()` ArchUnit 探针）。
- [ ] `t_proc_outbox` / `t_proc_processed_event` 中继与 DLQ 转投演练通过（不得复用 `IntegrationOutboxRelay` 的 `markFailed` 语义）。
- [ ] Webhook HMAC 四类负向注入全部拒绝。
- [ ] `tools/check_procura_alignment.py` 全套 + `--self-test` 双绿，并接入 CI（新增 workflow 或并入 `docs-compliance.yml`）。
- [ ] HC-001 在 CI 补齐 `bannedDependencies` 前，采购域新增代码的人工复核记录归档。

---

## 7. 现有 `bone-app-purchase` 的处置（P0 新增）

### 7.1 现状实证

| 项 | 结论 |
|----|------|
| 位置 | `bone-apps/bone-app-purchase/`，12 个主代码 + 测试类 |
| 形态 | 低代码 Runtime 动作 + 工作流适配器：`PurchaseWorkflowStatusPort.java:37 implements WorkflowStatusPort`、`PurchaseOrderPayHandler.java:39 implements RuntimeActionHandler`、`PurchaseWorkflowSubmitHandler.java:28`、`PurchaseWorkflowDecisionHandler.java:32`、`PurchaseWorkflowWithdrawHandler.java:30`、`PurchaseWorkflowRelayConfiguration.java` |
| 缺什么 | 无 `@Table`/`@Column` 实体、无 `Repository`、无 `ApplicationService`；`bone-init.sql` 无采购业务表（Grep `CREATE TABLE ... purchase\|procure\|supplier\|vendor` 零命中） |
| 同类参考 | `bone-apps/bone-app-expense/.../ExpenseWorkflowStatusPort.java:30`；`bone-blueprint/.../ReplenishmentOrder.java:25-47`（含 `supplierCode` 的实体闭环） |

### 7.2 三条路径与决策

| 路径 | 做法 | 评价 |
|------|------|------|
| **A（推荐）保留为适配层** | `bone-app-purchase` 继续承载低代码 Runtime 动作与 `WorkflowStatusPort`；新 `bone-procurement-*` 模块承载 Java 聚合/DDL/领域服务；两者经 `WorkflowStatusPort` 与领域事件对接 | 不推翻存量、不丢代码；与总体 §8.1 单应用多模块自洽 |
| B 迁移为 Java 聚合 | 把处理器改写为采购域实体与应用服务 | 需重写 DDL 与 Runtime 动作配置，收益低、风险高 |
| C 两套并存互不引用 | 各写各的 | 会产生两个「采购」真源与状态机，**禁止** |

**决策（写入总体 §4.2 / §8.1）**：采用 A。约束：

1. 新模块命名统一 `bone-procurement-*`，**不再新建 `bone-app-purchase-*` 兄弟模块**；
2. 采购域聚合状态回写统一经 `WorkflowStatusPort` 实现，**复用现有适配器的端口契约**，但新增 Java 聚合状态由采购域自己的 `WorkflowStatusPort` 实现负责，避免一份状态两处写；
3. 现有 `PurchaseOrderPayHandler`（付款动作）须按 PA-04 复核：采购域不作付款许可裁决，该动作保留为**低代码演示/占位**，一期生产链路以 `PaymentRequested` 事件为准，不得让 Runtime 动作直接触发付款；**为防误触发，须给该 Handler 加显式禁用开关（`bone-app-purchase` 配置项 `procura.purchase-pay-handler.enabled` 默认值 `false`）并在应用启动期 fail-closed（开关缺省/未配置即禁用），使代码层硬隔离——文档声明以 `PaymentRequested` 为准必须落到代码开关，避免生产链路被 Runtime 动作绕过付款裁决直接付款（诊断 F-E）**；
4. 迁移/退役路径列入二期 WBS，退役前不得删改存量类。

---

## 8. 业界最佳实践校准

| 实践 | 要点（来源） | 本方案落点 |
|------|-------------|-----------|
| **Golden path / paved road** | 复用能力必须做成带 CI、测试、可观测、密钥与安全默认值的**模板**，让正确路径比绕路更省事；平台即产品，采用率而非功能数是成功指标 | §3 最小闭环即采购域 golden path 模板；§2 三轨分派；§6 门禁内建；采纳指标 = 新模块从模板创建的比例 |
| **Guardrails, not handcuffs** | 只把不可协商项（加密、身份、租户、审计）编码为 policy，可逆选择留给团队，并留 escape hatch | §6 只强制 HC / 声明类门禁；业务建模不设机器约束（CORE-08） |
| **Policy as Code** | 安全与合规判据进流水线而非评审会 | §6.2 / §6.3 机器门禁 + 负向探针；`--self-test` 防恒绿 |
| **SAP Clean Core Level A–D（2025-08）** | A = 仅 released API 的 side-by-side / ABAP Cloud；C/D = 访问内部对象或写核心表，升级风险最高 | §2 三轨对应 Level A；「不直连/改写 ERP 表」= 拒绝 Level D；ACL 只用公开 API |
| **SAP AEM（Assess → Classify → Define）** | 按**耦合度**决定 renovate（on-stack）还是 re-platform（side-by-side），不是一律外置 | §7 现有适配层按耦合度保留而非重写；总体 §8.4 双跑割接按租户/法人/品类灰度 |
| **Outbox + Idempotent Consumer** | 发件原子 + 消费幂等 + DLQ，至少一次投递 | §5.1–§5.2；纠正「DLQ 已可用」的过度承诺 |
| **平台即产品的反向共建** | domain team 的缺口应回灌平台而非各自造轮子 | §4.4 共建三项；不建第二套引擎/任务中心/审计总线 |
| **SAP MDG-S / DAMA-DMBOK** | Golden Record、字段 Owner、survivorship | 领域 §3 / 总体 §6.1（v0.2 已落地，保留） |

> **与 v0.2 的关系**：v0.2 解决了「业务域设计对不对」，本文解决「平台能力声明真不真、代码怎么接、门禁能不能证明」。二者不冲突；冲突时**平台实证以本文为准**（本文的每条 S 态都带 `file:line`，平台源码变更须同步本文）。

---

## 9. 条款级修订映射与待决项

### 9.1 v0.2 → v0.3 处置对照

| v0.2 条款 | 处置 | 本文章节 |
|-----------|------|----------|
| 总体 PA-02 / §10 / §11.1 / §12.2；领域 §7.2 / §8.1 | 改写（撤回 4 条降级、PA-02 收窄依据更新、共建重排） | §4 |
| 总体 §4.2 模块表 / §8.1 部署单元 | 增补 `bone-app-purchase` 适配层与边界 | §7 |
| 总体 §5 技术栈 / 领域 §3 / §8 | 术语与能力状态校正 | §1.4、§2 |
| 总体 §13 / §12.2 / §14 | CI 与本地入口分离、门禁清单重列、exit criteria 增补 | §6 |
| 总体 §15.1 / 领域 §12 / README | 增加第四真源与脚本登记 | §0、§6.3 |
| 总体 §3.1 / PA-04 / PA-07 / PA-08 / §8.3 / §8.4；领域 §5.3 / §6.4 / §7.5–§7.9 | **保留不改** | §0 注 |

### 9.2 留给 owner 的待决项（未擅自拍板）

| # | 待决 | 需要谁 |
|---|------|--------|
| D1 | `bone-workflow-engine/README.md:147` 状态清单修订（文档漂移） | 平台组 |
| D2 | 通用 Outbox starter / DLQ 重放控制台是否立项共建 | 平台组 + 采购域 |
| D3 | `t_proc_processed_event` 是新建还是平台统一幂等表收口 | 架构组 |
| D4 | Webhook HMAC 验签回灌 `bone-integration` 的排期 | 平台组 |
| D5 | `bone-app-purchase` 的 `PurchaseOrderPayHandler` 退役或保留为演示 | 采购域 TL + 低代码 Runtime owner |
| D6 | ~~采购域门禁脚本接入 `ci.yml` 还是新建 workflow~~ → **已裁决（2026-11-10）**：接入 `ci.yml` 的 `backend-quality` 四条 blocking 步骤 + `check.sh` G0；可复制片段见 `一期边界与机制触发条件.md §5` | 平台组（已闭合） |
| D7 | RA-2 账套/采购组织清单、§8.3 NFR 数值、一期 DDL 与事件 payload 冻结 | 财务域 / 客户 SLA 评审（沿用总体 §12.2） |

---

> **维护约定**：平台源码或 DDL 变更后，先复核本文 §1 的证据 `file:line` 是否仍成立，再更新总体/领域方案；`tools/check_procura_alignment.py --self-test` 必须随判据变更同步扩充负向探针，禁止只加判据不加反证。
