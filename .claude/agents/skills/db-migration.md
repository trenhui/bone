---
name: db-migration
description: 数据库表结构与迁移脚本技能包（Bone：MySQL + 手写 SQL，无 Flyway/Liquibase）
---

> 规范真源：[`doc/deployment/sql/README.md`](../../../doc/deployment/sql/README.md)、
> [`doc/agents/05-数据库与安全.md`](../../../doc/agents/05-数据库与安全.md)、
> [`doc/architecture/数据库开发规范.md`](../../../doc/architecture/数据库开发规范.md)。
> **本项目不使用 Flyway / Liquibase**——迁移是手写 SQL 脚本 + 人工按序执行。

## 职责
- 根据数据模型变更产出**幂等** SQL 脚本。
- 检查破坏性变更（DROP / ALTER COLUMN 改类型 / RENAME / TRUNCATE）。
- 脚本头部写清变更目的、影响范围、回滚方案。

## 数据库
- MySQL（InnoDB，`utf8mb4` / `utf8mb4_unicode_ci`），Schema 名 `bone`。

## 脚本落点（三者分工）
| 场景 | 落点 |
|---|---|
| 全量初始化（新环境） | `bone-init.sql`（仓库根，**DDL 唯一真源**） |
| 核心平台 / 中台表结构 | `doc/deployment/sql/{bonecore,bone-midplatform,midplatform}.sql` |
| 已部署环境增量升级 | `doc/deployment/sql/migrations/` |

## 命名规范
```
V{YYYYMMDD}_{序号}__{简要描述}.sql
```
- `V` 固定前缀；`YYYYMMDD` 为脚本创建日；`序号` 三位补零（`001`）；`__` 双下划线；`描述` 小写下划线。
- 示例：`V20260619_001__add_tenant_config_table.sql`。
- 增量脚本置于 `doc/deployment/sql/migrations/`；复制 `V20260619_001__template.sql` 后修改即模板范式。

## 执行规则
1. **新环境**：直接执行 `bone-init.sql`（全量建表 + 初始数据），**不执行** migrations。
2. **已部署升级**：按日期 + 序号顺序执行 `migrations/` 下的脚本。
3. **不可修改已发布脚本**：已在生产执行过的迁移不可改，新变更必须新建脚本。
4. **每个脚本必须可重复执行**：用 `CREATE TABLE IF NOT EXISTS`、`ADD COLUMN IF NOT EXISTS` 等幂等语法。
5. **脚本头注释必填**（目的 / 影响范围 / 回滚方案）。
6. 变更完成后在 `doc/deployment/sql/README.md` 的「迁移记录」表登记一行。

## 表设计共性（所有业务表）
- `tenant_id`（租户隔离）
- `created_at`、`created_by`、`updated_at`、`updated_by`（审计）
- `deleted` `TINYINT`（软删除，默认 0）
- JSON 类型字段（灵活 schema）

> 实体侧对应 `TenantAbstractEntity` / `AbstractEntity`（见 E-6 / HC-008），新增表须与实体基类字段对齐。

## 脚本模板
```sql
-- ============================================================
-- 迁移脚本: V20260619_001__add_tenant_config_table
-- 日期: 2026-06-19
-- 目的: 新增租户配置表，支持租户级参数管理
-- 影响范围: bone 库
-- 回滚方案: DROP TABLE IF EXISTS tenant_config;
-- ============================================================

CREATE TABLE IF NOT EXISTS tenant_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    tenant_id   BIGINT       NOT NULL COMMENT '租户ID',
    config_key  VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT        COMMENT '配置值',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户配置表';
```

## 破坏性变更须人工确认（DDL 属 L3，需架构师审批）
- `DROP TABLE` / `DROP COLUMN`
- `ALTER COLUMN` 改数据类型
- `RENAME TABLE` / `RENAME COLUMN`
- `TRUNCATE`
- 大表加索引须评估锁表风险。

## 安全 / 诚实性提醒
- 删列前先「加新列 → 数据迁移 → 删旧列」；加 `NOT NULL` 列须带默认值。
- `biz_identity_code` 存在于 SDK 代码与部署 SQL（`doc/deployment/sql/bonecore.sql`），但 `bone-init.sql` 尚未包含。
- `scripts/check-ddl-doc-sync.py` **只比对表名清单、不读列**，且没有工作流调用 ⇒ 必备字段（tenant_id / 审计 / deleted）目前**靠人审**，不要宣称已由 CI 阻断。
