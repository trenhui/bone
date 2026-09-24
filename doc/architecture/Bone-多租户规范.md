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

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-20 | §4 新增「实体声明」规则 + §7 对应检查项：SDK 按实体字段判定租户表，DDL 有 `tenant_id` 不等于查询会带租户条件 |
| 2026-05-17 | 初版；对齐总体架构 §8.5 |
| 2026-05-20 | 明确 `biz_identity_code` 默认仅在 JWT/上下文，不强制落库；需落库须 ADR |
