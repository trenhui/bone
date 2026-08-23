# bone-iam 与 bone-system 模块边界规范

## 问题背景

bone-iam 的 `iam_audit_log`（用户操作审计）与 bone-system 的 `sys_log`（系统日志）在"审计日志"维度存在职责重叠，开发时不确定应该写入哪个表。本文档明确两者的职责边界。

## 职责划分

### bone-iam（身份与访问管理）

**核心职责**: 认证、授权、账户管理、租户管理

**iam_audit_log 记录范围** — 仅记录与**身份认证和权限**相关的操作：

| 操作类型 | 示例 | 写入表 |
|----------|------|--------|
| 登录/登出 | 用户登录成功/失败、Token 刷新、会话撤销 | iam_audit_log |
| 账户变更 | 创建/删除/启用/禁用账号、重置密码 | iam_audit_log |
| 权限变更 | 角色分配/撤销、权限分配/撤销 | iam_audit_log |
| 租户变更 | 创建/删除/启用/禁用租户、配额调整 | iam_audit_log |
| SSO/MFA | SSO 登录、MFA 验证 | iam_audit_log |

**字段**: user_id, operation, resource_type, resource_id, ip, user_agent, result（成功/失败）

### bone-system（系统管理）

**核心职责**: 系统配置、系统级日志聚合、监控告警、平台运维

**sys_log 记录范围** — 记录**系统运行时**事件（非用户操作）：

| 日志类型 | 示例 | 写入表 |
|----------|------|--------|
| 系统异常 | 服务启动失败、数据库连接超时、第三方调用异常 | sys_log |
| 运行日志 | 定时任务执行、缓存刷新、数据同步 | sys_log |
| 安全告警 | 异常 IP 访问频次超限、SQL 注入检测 | sys_log |
| 性能日志 | 慢查询、接口超时 | sys_log |

**字段**: level（ERROR/WARN/INFO/DEBUG）, service, content, trace_id

**sys_config**: 系统级配置项（如全局开关、阈值配置），不包含用户/租户级配置

## 判定规则

```
操作是否由用户主动发起？
├── 是 → 操作是否涉及身份/权限/账户/租户？
│       ├── 是 → iam_audit_log (bone-iam)
│       └── 否 → 如果是业务操作（如创建订单），由对应业务模块记录
└── 否 → sys_log (bone-system) — 系统自动产生的运行时事件
```

## 禁止事项

1. bone-system **不得**记录用户登录、权限变更等操作到 sys_log（这些属于 iam_audit_log）
2. bone-iam **不得**记录系统异常、定时任务等运行时事件到 iam_audit_log（这些属于 sys_log）
3. 两个模块都**不得**写入对方的表

## 后续建议

- 若后续需要统一审计查询，可在 bone-system 中建一个审计日志聚合视图（跨库 JOIN），但写入仍由各自模块负责
- 考虑为 iam_audit_log 添加 level 字段，与 sys_log 的日志级别体系统一
