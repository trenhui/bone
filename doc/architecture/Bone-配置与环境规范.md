# Bone 配置与环境规范

> **文档性质**：12-Factor 配置、Profile、环境变量与密钥管理的**工程真源**。  
> **更新**：2026-05-17  
> **操作入口**：[config/env/README.md](../../config/env/README.md)、[`.env.example`](../../.env.example)

---

## 1. 原则（12-Factor III / IV）

| # | 规则 |
|---|------|
| 1 | **配置与代码分离**；不进 Git 的只放 `.env` / 密钥管理系统 |
| 2 | **环境等价**：dev/test/prod 同一套变量名，值不同 |
| 3 | **无静默默认密码**：生产禁止 `application.yml` 写死 `root/123456` |
| 4 | **启动可观测**：缺必填变量 **fail-fast**，禁止连错库 |

---

## 2. 配置层次

```
优先级（高 → 低）：
  操作系统环境变量 / K8s Secret
  → SPRING_PROFILES_ACTIVE 对应 application-{profile}.yml
  → application.yml（仅 ${VAR:default} 占位）
```

| 层 | 位置 | 可提交 Git |
|----|------|------------|
| 默认值 | `application.yml` 中 `${BONE_DB_URL:jdbc:...}` | 是（无秘密） |
| 环境覆盖 | `application-dev.yml` | 是（无秘密） |
| 本地秘密 | 仓库根 `.env` | **否**（`.gitignore`） |
| 生产秘密 | K8s Secret / 配置中心 | 否 |

---

## 3. 命名约定

| 前缀 | 用途 | 示例 |
|------|------|------|
| `BONE_DB_*` | 数据库 | `BONE_DB_URL`, `BONE_DB_PASSWORD` |
| `BONE_REDIS_*` | 缓存 | `BONE_REDIS_HOST` |
| `BONE_JWT_*` / `BONE_IAM_*` | 认证 | `BONE_JWT_SECRET` |
| `BONE_SERVER_PORT` | 服务端口 | `8080`（示例；各模块默认见 [wiki/03](../wiki/03-本地开发与构建.md) 与对应 `application.yml`） |
| `BONE_INTEGRATION_JWT_ENABLED` | 功能开关 | `true`/`false` |
| `BONE_INTEGRATION_ALERT_ENABLED` | 集成领域事件告警 | `true`/`false` |
| `BONE_ALERT_DINGTALK_ENABLED` / `DINGTALK_WEBHOOK` | 钉钉告警通道 | — |
| `BONE_ALERT_MAIL_ENABLED` / `BONE_ALERT_MAIL_*` | 邮件告警通道 | — |
| `SPRING_PROFILES_ACTIVE` | Profile | `dev`, `prod` |

**禁止**：同一含义多个变量名（如 `DB_PASSWORD` 与 `BONE_DB_PASSWORD` 并存且无文档）。

---

## 4. Profile

| Profile | 用途 |
|---------|------|
| `dev` | 本地默认；可连 Docker MySQL/Redis |
| `test` | CI / 单测；内存库或 Testcontainers |
| `prod` | 生产；仅环境变量，无 dev 默认值 |

模块特化：`metadata-mysql`、`in-memory` 等须在模块 README 说明，且**不**作为生产 Profile。

---

## 5. 密钥管理

| 环境 | 做法 |
|------|------|
| 本地 | `cp .env.example .env` + `source scripts/dev/load-env.sh` |
| CI | GitHub/Gitee Secrets 注入 `BONE_*` |
| 生产 | Secret 挂载；JWT/DB 定期轮换 |

见 [Bone-安全开发规范](./Bone-安全开发规范.md) §4。

---

## 6. 功能开关

| 规则 | 说明 |
|------|------|
| 命名 | `BONE_{MODULE}_{FEATURE}_ENABLED` |
| 默认 | 安全相关默认 **false**（如集成 JWT 默认关闭便于 dev） |
| 文档 | 新增开关须写入 `config/env/README.md` 变量表 |

---

## 7. 检查清单

- [ ] 无新增明文密钥进 Git  
- [ ] `application.yml` 使用 `${BONE_*}`  
- [ ] `.env.example` 已同步新变量（无真实值）  
- [ ] 生产 Profile 无 dev 默认密码  
- [ ] 本地 README 已链到本文  

---

## 8. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-05-17 | 初版；与 config/env 对齐 |
