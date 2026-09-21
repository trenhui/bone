# 架构决策记录（ADR）

破坏性变更 DDD / API 门禁时，在 `doc/architecture/adr/` 新增记录，并同步 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md) E-0.3 所列下游文档。

命名建议：`NNNN-short-title.md`（递增序号）。

| ADR | 标题 |
|-----|------|
| **0001~0010** | **IAM 模块演进**（以下 10 个 ADR 记录 bone-iam 的架构决策轨迹） |
| [0001](./0001-database-ddl-single-source.md) | 数据库 DDL 单轨真源（`bone-init.sql`） |
| [0002](./0002-iam-rbac-jwt-authorities.md) | 默认 RBAC + 权限码作为 JWT authorities |
| [0003](./0003-iam-five-layer-policy.md) | 五层权限模型分阶段交付 |
| [0004](./0004-iam-authz-service-evolution.md) | PolicyEvaluator 进程内 → 独立 authz-service |
| [0005](./0005-iam-jwt-rs256-jwks.md) | JWT HS256 → RS256/EdDSA + JWKS 轮换 |
| [0006](./0006-iam-tenant-isolation-modes.md) | 多租户隔离三模式切换策略 |
| [0007](./0007-iam-platform-audit-bus.md) | 平台审计总线（AOP + WORM）落地路径 |
| [0008](./0008-iam-argon2id-password-hash.md) | 密码哈希 BCrypt → Argon2id 迁移 |
| [0009](./0009-iam-audit-log-schema-split.md) | `iam_audit_log` 扩列 vs 平台审计总线职责 |
| [0010](./0010-iam-account-direct-permission.md) | 账号直连权限是否纳入 MVP |

| [0011](./0011-aggregate-root-inheritance.md) | AggregateRoot 继承 AbstractEntity |
| [0012](./0012-system-exception-hierarchy.md) | SystemException 归属基础设施异常链 |
| [0013](./0013-extension-studio-repository-read-side.md) | extension-studio 仓储读侧拆 ReadPort |
| [0014](./0014-extension-rollout-traffic-canonical.md) | 扩展灰度字段收敛 — `config_json.traffic` 唯一语义源 |
| [0015](./0015-metadata-runtime-jdbc-via-engine.md) | 元数据模式 B 运行时 JDBC（engine） |
| [0016](./0016-metadata-catalog-abstract-entity-tenant.md) | 元数据 catalog AbstractEntity + tenantId |
| [0017](./0017-masterdata-catalog-sync.md) | 业务元数据实体（`meta_*`）→ 主数据实体（`mdm_*`）同步范式 |
| [0018](./0018-iam-localdatetime-audit.md) | bone-iam LocalDateTime 审计策略 |
| [0019](./0019-id-generation-contract-respect-non-null-id.md) | ID 生成契约 — SDK `insert` 尊重非空 id（**已接受 / 已落地**） |
| [0020](./0020-anti-anemia-weak-form-strong-behavior.md) | 反贫血机制 — 弱约束代码形式、强约束行为（**提议**） |
| [0021](./0021-outbox-and-consumer-idempotency-platformization.md) | Outbox 与消费端 `eventId` 幂等平台化（**提议**） |
| [0022](./0022-external-callback-signature-verification-port.md) | 外部回调验签端口化 — 由支付样板推广为全局硬规则（**提议**） |
| [0023](./0023-core-domain-smart-metadata.md) | Bone 当前核心域定为 Smart Metadata（**已接受**） |
| [0024](./0024-ddd-v5-rule-semantics-and-document-split.md) | DDD v5.0 规则语义校准与文档分册（**已接受**） |
| [0025](./0025-ddd-v5-0-2-implementation-alignment.md) | DDD v5.0.2 规则标识与实现状态对齐（**已接受**） |
| [0026](./0026-ddd-single-document-consolidation.md) | DDD v5.1.0 单文档整合（**已接受，取代 ADR-0024 的分册决定**） |
| [0027](./0027-module-layer-semantics-before-physical.md) | 模块层级语义先立、物理结构后收（Kernel/Framework/Engine/Platform，**已接受**） |
| [0028](./0028-application-service-first-selective-cqrs.md) | Application Service First + Selective CQRS（**已接受**） |
| [0029](./0029-sdk-auto-tenant-filter.md) | SDK 查询/更新/删除自动注入 tenant_id（落实多租户规范 §3，**已接受 / 已实现**） |
| [0030](./0030-domain-repository-read-merge.md) | 单一仓储 + 外置 `.sql` 优先 + `@TenantScope` 自动租户注入（**草案 · 待架构组批准**） |
| [0031](./0031-sdk-optimistic-lock-and-async-tenant-context.md) | 写路径租户护栏补全 + SDK 原生乐观锁（`@Version`）+ 异步入口租户声明（**提议 · D0 已落地，D1~D3 待批准**） |
| [0032](./0032-controlled-batch-convergence.md) | 受控批量收敛通道 — 一次性批量重构的授权与登记（**已采纳**，含 bone-iam 2026-09-20 先例） |
| [0033](./0033-application-collaboration-service.md) | 应用层协作服务（`application/service`）的定位与落点判据（**已采纳**，IAM 现有 9 个类就地合规） |
| [0034](./0034-tenant-scope-explicitness-and-all-entry-gate.md) | 租户隔离显式性 — SQL 通道启动期 fail-fast + 全租户入口单一判据（**已采纳**，首轮抓出 IAM 登录入口与 integration 定时任务两处问题） |
