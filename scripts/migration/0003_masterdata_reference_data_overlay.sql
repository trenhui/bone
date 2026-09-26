-- ============================================================
-- 0003_masterdata_reference_data_overlay.sql
-- 参考数据 overlay 拆分（2026-09-26 裁决，多租户规范 §8 约束 6）：
--   mdm_reference_set         保持「平台全局目录」，唯一键 uk_mdm_refset_code 收敛为 (set_code)
--   mdm_reference_value       保持平台值表（tenant_id 列保留仅为 DDL 基线，恒 0）
--   mdm_reference_value_tenant 新增租户私有扩展值表（tenant_id, set_id, value_code 唯一）
-- 依据：doc/design/modules/3a §2.3 + doc/architecture/Bone-多租户规范.md §8
-- 前置：表结构以 bone-init.sql §8 为准；本脚本负责存量数据搬迁与索引收敛。
-- 执行前：必须备份（见「阶段 0」与文件末尾「回滚」）。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
-- 安全约定：只用 CREATE TABLE IF NOT EXISTS，绝不可先 DROP 备份表（同 0001 教训）。
CREATE TABLE IF NOT EXISTS bak_mdm_reference_set_0003 AS SELECT * FROM mdm_reference_set;
CREATE TABLE IF NOT EXISTS bak_mdm_reference_value_0003 AS SELECT * FROM mdm_reference_value;

-- ---------- 阶段 1：Expand（新增租户私有值表） ----------
CREATE TABLE IF NOT EXISTS mdm_reference_value_tenant (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID（租户私有扩展值，SDK 严格过滤/回填）',
    set_id              BIGINT          NOT NULL COMMENT '值域ID（引用平台值域 mdm_reference_set.id）',
    value_code          VARCHAR(100)    NOT NULL COMMENT '值编码（不得与同值域平台值/其他租户已用编码重码，应用服务跨表校验）',
    value_name          VARCHAR(200)    NOT NULL COMMENT '值名称',
    external_code       VARCHAR(100)    DEFAULT NULL COMMENT '外部标准码（ISO/GB），租户私有值可空',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_refval_tenant (tenant_id, set_id, value_code),
    KEY idx_mdm_refval_tenant_set (set_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='参考数据租户私有扩展值（2026-09-26 overlay 拆分，§8 约束6）';

-- ---------- 阶段 2：Migrate（混存表中的租户私有值 → 租户表） ----------
-- 值编码在值域内全局唯一（旧 uk(set_id, value_code)），不存在跨租户同编码行，可安全平移。
INSERT INTO mdm_reference_value_tenant
    (id, tenant_id, set_id, value_code, value_name, external_code, sort_order,
     enabled, created_by, created_at, updated_at, deleted, version)
SELECT id, tenant_id, set_id, value_code, value_name, external_code, sort_order,
       enabled, created_by, created_at, updated_at, deleted, version
FROM mdm_reference_value
WHERE tenant_id <> 0;

DELETE FROM mdm_reference_value WHERE tenant_id <> 0;

-- ---------- 阶段 3：Contract（值域唯一键收敛为全局 set_code） ----------
-- 值域为平台全局目录（tenant_id 恒 0），(tenant_id, set_code) 语义上退化为 (set_code)。
ALTER TABLE mdm_reference_set
    DROP INDEX uk_mdm_refset_code,
    ADD UNIQUE KEY uk_mdm_refset_code (set_code);

-- ---------- 回滚 ----------
-- 1) 数据回滚（先于 DDL）：
--    INSERT INTO mdm_reference_value
--      (id, tenant_id, set_id, value_code, value_name, external_code, sort_order,
--       enabled, created_by, created_at, updated_at, deleted, version)
--    SELECT id, tenant_id, set_id, value_code, value_name, external_code, sort_order,
--           enabled, created_by, created_at, updated_at, deleted, version
--    FROM mdm_reference_value_tenant;
--    （或直接从 bak_mdm_reference_value_0003 全量恢复两张旧表）
-- 2) DDL 回滚：
--    DROP TABLE mdm_reference_value_tenant;
--    ALTER TABLE mdm_reference_set
--        DROP INDEX uk_mdm_refset_code,
--        ADD UNIQUE KEY uk_mdm_refset_code (tenant_id, set_code);
