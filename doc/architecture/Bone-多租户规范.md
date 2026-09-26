# Bone 多租户规范

> **文档性质**：`tenant_id`、`biz_identity_code` 在身份、上下文、数据访问与日志中的**一致传递**。  
> **更新**：2026-05-17  
> **关联**：[数据库开发规范.md](./数据库开发规范.md)、[Bone-安全开发规范.md](./Bone-安全开发规范.md)、`TenantContext`（`bone-core`）

---

## 1. 概念

| 字段 | 说明 | 实现形态（As-Is） |
|------|------|-------------------|
| `tenant_id` | 租户隔离主键；表列 + JWT claim | **必落库**：所有业务表强制；见 [数据库开发规范 §1](./数据库开发规范.md) |
| `biz_identity_code` | 租户下业务身份/条线；细粒度隔离 | **As-Is：仅上下文/JWT**（`BizIdentityContext`、`ExtensibleObject.bizIdentityCode`），**默认不进 DDL**；如需表级落库须模块详设说明并补 DDL（**[Target]**） |

> **重要差异**：当前 `bone-init.sql` 与 `TenantAbstractEntity` **不含** `biz_identity_code` 列；该字段作为 JWT/上下文/扩展元数据传递。若模块业务必须按 `biz_identity_code` 物理分区或建唯一约束，须先提 ADR 并在 `数据库开发规范` 登记字段约束（避免一刀切引入空列）。

---

## 2. 信任链（强制）

```
客户端 → 网关 → JWT 解析 → TenantContext → Repository/SQL 拦截器
```

| 规则 | 说明 |
|------|------|
| 来源 | `tenant_id` **仅从可信身份**（JWT / 网关注入头） |
| 禁止 | 请求体 `tenantId` 覆盖已认证租户 |
| 头 | `X-Tenant-Id`、`X-Biz-Identity-Code` 由网关注入；下游只读 |

---

## 3. 运行时上下文

```java
// bone-core: TenantContext（ThreadLocal）
TenantContext.setTenantId(...);
TenantContext.setBizIdentityCode(...);
// 请求结束必须 clear，防止线程池泄漏
```

| 层 | 职责 |
|----|------|
| Filter | 从 JWT 写入 `TenantContext` + MDC `tenantId` |
| Application | 不重复解析租户；使用上下文 |
| Infrastructure | SQL 自动附加 `tenant_id = ?`（拦截器 / SDK） |

---

## 4. 数据访问

| 规则 | 说明 |
|------|------|
| **实体声明** | 租户表对应的聚合必须继承 `TenantAggregateRoot`（实体继承 `TenantAbstractEntity`），**在实体上声明 `tenantId`**。SDK 按**实体字段**判定租户表（`TableMetadataResolver` → `TableMetadata.isTenantScoped()`），DDL 有 `tenant_id` 列但实体不声明 ⇒ `TenantFilterInjector` 直接返回、查询**不注入任何租户条件**——这是静默跨租户读的成因（2026-09-20 实测：bone-integration 5 个聚合全 `AggregateRoot`，`int_*` 表却有 `tenant_id NOT NULL`，用户可达的 `/executions`、`/statistics` 均无租户过滤，见该模块 README 的登记缺口） |
| 查询 | 所有业务查询带 `tenant_id`；`biz_identity_code` **仅当表已落库该列**时附加过滤，否则在应用层按上下文判断 |
| 写入 | 插入时从上下文填充 `tenant_id`，禁止客户端指定他人租户；`biz_identity_code` 写入需先确认 DDL 已包含该列 |
| 跨租户 | 仅平台超管角色；须审计 + Scope `platform:*` |
| 缓存 Key | `bone:{tenantId}:{domain}:{resource}:{id}[:{facet}]`（以 [Bone-缓存规范 §2](./Bone-缓存规范.md) 为准）；`biz_identity_code` 默认不落库，**不**纳入 Key，避免基数膨胀 |

---

## 5. 消息与事件

- 信封必含 `tenantId`（[Bone-消息与事件规范](./Bone-消息与事件规范.md)）  
- 分区键默认 `tenant_id`  

---

## 6. 测试

| 类型 | 要求 |
|------|------|
| 单测 | `@BeforeEach` 设置/清理 `TenantContext` |
| 集成 | 用例 A 租户数据对租户 B **不可见** |
| ArchUnit | 禁止 application 层硬编码 `tenant_id = 'default'` 绕过上下文 |

---

## 7. 检查清单

- [ ] 新表含 `tenant_id`（+ 审计列）  
- [ ] 租户表的聚合**在实体上声明 `tenantId`**（继承 `TenantAggregateRoot` / `TenantAbstractEntity`）——DDL 有列 ≠ SDK 认租户表，漏了这条等于查询无租户过滤（见 §4 实体声明）  
- [ ] API 不信任 body 租户字段  
- [ ] MDC 有 `tenantId`  
- [ ] 缓存/MQ 带租户维度  
- [ ] 跨租户测试用例  
- [ ] 如需 `biz_identity_code` 物理落库：已提 ADR + 更新 `数据库开发规范`  

---

## 8. 主数据模块的租户模型（2026-09-26 补）

主数据（bone-masterdata）采用「**平台模板层 + 租户实例层**」两层租户模型（裁决见 [3a. 主数据管理模块核心场景及用例设计方案](../design/modules/3a.%20主数据管理模块核心场景及用例设计方案.md) §1.1/§2）：

| 层 | 表 | tenant_id | 语义 |
|----|----|-----------|------|
| 平台模板层 | `mdm_domain_template` / `mdm_template_version` / `mdm_reference_set` / `mdm_reference_value` | `0` | 平台维护，租户**只读实例化 / 只读引用**，不走租户审批流 |
| 租户实例层 | `mdm_entity` / `mdm_field` / `mdm_record` / `mdm_category` / `mdm_steward` / `mdm_entity_subscription` / `mdm_quality_issue` / `mdm_model_drift` / `mdm_feedback` | 租户 ID | 行级隔离，SDK 自动过滤/回填 |

约束：

1. 模板实例化（UC-T1）落到租户时，`tenant_id` 由 SDK 回填租户上下文，实体携带 `template_id` / `template_version` 溯源，**不复制**平台行。
2. 参考数据值采用 **overlay 两层模型（2026-09-26 裁决，第 7 条）**：平台标准值在 `mdm_reference_value`（非租户作用域，对全体租户可见），租户私有扩展值在 `mdm_reference_value_tenant`（租户作用域，SDK 严格过滤/回填）。值编码在值域内**跨两层全局唯一**（服务层跨表查重）——租户私有值不得覆盖平台值编码，读取按值域合并即纯并集，无 shadowing 歧义。
3. 审批（SoD）、订阅批准等治理动作全部发生在**租户内**，不跨租户；跨租户只允许平台治理巡检只读视图。
4. **平台模板目录为非租户作用域（2026-09-26 实测裁决）**：`DomainTemplate` / `TemplateVersion` 聚合**不继承 `TenantAggregateRoot`、不映射 `tenant_id`**（DDL 列保留，DEFAULT 0）。理由：该表是「平台只写（`masterdata:templates:write` 为平台域权限）、多方只读」的全局目录，行内容恒为平台资产。若做成租户作用域，SDK Criteria 通道注入严格 `tenant_id = :current` 过滤，租户读 `tenant_id=0` 平台行 404、实例化不可用（实测复现）；而 FluentQuery 通道又不过滤，两通道行为分裂。守护测试：`DomainTemplateTenantVisibilityTest`（含「租户作用域聚合隔离未被破坏」的对照组）。
5. **（已裁决，落地为第 7 条）**。
7. **参考数据混存表按 overlay 拆分（2026-09-26 裁决，ADR-0029 不变量保持不变）**：原「平台值 + 租户扩展值」混存不引入 SDK「共享行」语义（`tenant_id IN (0, :current)` 会成为第二个逃生舱、弱化 fail-closed 不变量），改为**拆表**——`ReferenceSet` / `ReferenceValue` 去租户作用域（同第 4 条先例，值域为平台全局目录、仅平台管理员可写），新增租户作用域聚合 `TenantReferenceValue`（`mdm_reference_value_tenant`）。写路径按当前租户分发（`CurrentUserPort.requireTenantId()`：tenant=0 写平台表、其余写租户表），越权写抛 `MD_REF_PLATFORM_SET_IMMUTABLE` / `MD_REF_PLATFORM_VALUE_IMMUTABLE`（403）；读路径按值域合并两层为 `ReferenceValueView`（带 `scope` 来源标识）。迁移：`scripts/migration/0003_masterdata_reference_data_overlay.sql`。守护测试：`ReferenceDataOverlayTest`（可见性/隔离/跨表查重/越权守卫/平台视角五组）。
6. **FluentQuery（DSL）通道已纳入租户注入（2026-09-26 修复，ADR-0029 补口）**：`SqlBuilder` 在 WHERE 构建期经 `TenantFilterInjector.resolveDslTenantValue` 注入租户谓词，与 Criteria 通道同不变量——可信 `TenantContext` 优先（caller 传入的 tenantId 条件被忽略并 WARN）&gt; caller 显式 EQ 兜底（后台任务链路）&gt; 失败关闭（`MissingTenantContextException`）。caller 条件整体加括号后再 AND 租户条件，防 OR 分组击穿；非租户表（未映射 `tenant_id`，如平台模板目录）永不注入。DSL 通道**无逃生舱**——跨租户基础设施扫描必须走 Criteria 通道（受架构门禁约束）。契约测试：`FluentQueryTenantScopeTest`。
8. **平台管理员租户切换（2026-09-26 新增，ADR-0029 fail-closed 下的平台全局视图正式入口）**：平台管理员（JWT claim `tenantId=0` 且持有 `iam:tenants:read`）可通过请求头 `X-Acting-Tenant-Id` 指定生效租户，由 `AbstractJwtAuthenticationFilter` 在**源头**把 `X-Tenant-Id` 视图改写为切换值——`TenantInterceptor`、SDK 数据源路由等全部下游消费者无感知、顺序无关。安全不变量：**租户 token（claim≠0）携带切换头一律无效**，仍强制归一化为自身 claim；无切换权限码的平台账号同样被忽略；非法值（非数字/≤0）忽略回退平台租户；每次切换留审计日志。该机制**不新增 SDK 逃生舱**（`disableTenantFilter` 门禁不变），模块侧 `CurrentUserPort.currentTenantId()` 在 claim=0 时以 `TenantContext`（即生效租户）为准。契约测试：`JwtAuthenticationFilterTenantHeaderTest`（归一化 + 切换 9 用例）。

---

## 9. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-26 | 新增 §8 主数据租户模型（平台模板层 + 租户实例层，G1 收敛后的正式结论）；§8 补记「平台模板目录非租户作用域」实测裁决；同日修复 FluentQuery 通道缺口（新增 §8 约束 6，ADR-0029 补口）；同日裁决参考数据混存表为 overlay 拆分（§8 约束 7，ADR-0029 不变量保持不变，`TenantReferenceValue` + 迁移 0003）；同日新增 §8 约束 8 平台管理员租户切换（`X-Acting-Tenant-Id`，平台全局视图正式入口） |
| 2026-09-20 | §4 新增「实体声明」规则 + §7 对应检查项：SDK 按实体字段判定租户表，DDL 有 `tenant_id` 不等于查询会带租户条件 |
| 2026-05-17 | 初版；对齐总体架构 §8.5 |
| 2026-05-20 | 明确 `biz_identity_code` 默认仅在 JWT/上下文，不强制落库；需落库须 ADR |
