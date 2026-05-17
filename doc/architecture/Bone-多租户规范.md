# Bone 多租户规范

> **文档性质**：`tenant_id`、`biz_identity_code` 在身份、上下文、数据访问与日志中的**一致传递**。  
> **更新**：2026-05-17  
> **关联**：[数据库开发规范.md](./数据库开发规范.md)、[Bone-安全开发规范.md](./Bone-安全开发规范.md)、`TenantContext`（`bone-core`）

---

## 1. 概念

| 字段 | 说明 |
|------|------|
| `tenant_id` | 租户隔离主键；表列 + JWT claim |
| `biz_identity_code` | 租户下业务身份/条线；细粒度隔离 |

所有业务表（除平台级字典）须含上述列，见数据库规范。

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
| 查询 | 所有业务查询带 `tenant_id`（及需要的 `biz_identity_code`） |
| 写入 | 插入时从上下文填充，禁止客户端指定他人租户 |
| 跨租户 | 仅平台超管角色；须审计 + Scope `platform:*` |
| 缓存 Key | `{tenantId}:{bizCode}:...`（见 [Bone-缓存规范](./Bone-缓存规范.md)） |

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
- [ ] API 不信任 body 租户字段  
- [ ] MDC 有 `tenantId`  
- [ ] 缓存/MQ 带租户维度  
- [ ] 跨租户测试用例  

---

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版；对齐总体架构 §8.5 |
