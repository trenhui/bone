# Bone 数据库迁移脚本管理规范

## 目录结构

```
doc/deployment/sql/
├── README.md                          -- 本文件
├── bone-init.sql                      -- 全量初始化脚本（新环境使用）
├── bonecore.sql                       -- 核心平台表结构
├── bone-midplatform.sql               -- 中台表结构
├── midplatform.sql                    -- 中台补充脚本
└── migrations/                        -- 增量迁移脚本（已部署环境升级用）
    ├── V20260528_001__meta_catalog_version_columns.sql
    ├── V20260619_001__template.sql    -- 模板（复制后修改）
    └── ...
```

## 命名规范

```
V{YYYYMMDD}_{序号}__{简要描述}.sql
```

| 部分 | 说明 | 示例 |
|------|------|------|
| `V` | 固定前缀 | `V` |
| `YYYYMMDD` | 日期，脚本创建日 | `20260619` |
| `序号` | 当日序号，三位补零 | `001`、`002` |
| `__` | 双下划线分隔符 | `__` |
| `简要描述` | 小写下划线命名 | `add_user_avatar_column` |

**示例**: `V20260619_001__add_tenant_config_table.sql`

## 执行规则

1. **新环境**: 直接执行 `bone-init.sql`（全量建表 + 初始数据），无需执行 migrations
2. **已部署环境升级**: 按日期+序号顺序执行 `migrations/` 下的脚本
3. **不可修改已发布的脚本**: 已在 生产环境执行过的迁移脚本不可修改，新变更必须新建脚本
4. **每个脚本必须可重复执行**: 使用 `IF NOT EXISTS`、`ADD COLUMN IF NOT EXISTS` 等幂等语法
5. **脚本头部必须包含注释**: 说明变更目的、影响范围、回滚方案

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

## 迁移记录

| 日期 | 脚本 | 变更内容 | 执行人 |
|------|------|----------|--------|
| 2026-05-28 | V20260528_001__meta_catalog_version_columns | meta_field / meta_entity_relation 增加乐观锁 version 列 | - |
