-- ============================================================
-- 0007_dict_hierarchy_split.sql
-- 字典 v2（父指针内联） → v3（值扁平 + 层级关系独立）
--   sys_dict_item.parent_code  迁出到 sys_dict_hierarchy（默认 DEFAULT 层级，含 path/level）
--   sys_dict_item_text         新增（SAP T005T / Oracle _TL 风格的多语言译文）
--   sys_dict_item              新增 effective_from/effective_to（Oracle 时间有效性）、external_code
--   sys_dict_type              新增 value_type/value_regex/code_segments（SAP Domain 技术属性 + GB/T 2260 编码分段）
-- 依据：doc/design/modules/7a. 数据字典模块详细设计方案.md §1（模型选型）§4（数据模型）
-- 前置：0006 已执行；本脚本幂等，可重复执行。
-- 执行前：必须备份（见「阶段 0」）。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_sys_dict_item_0007 AS SELECT * FROM sys_dict_item;
CREATE TABLE IF NOT EXISTS bak_sys_dict_type_0007 AS SELECT * FROM sys_dict_type;

-- ---------- 阶段 1：Expand（加列 / 建表） ----------
ALTER TABLE sys_dict_item
    ADD COLUMN external_code  VARCHAR(100)    DEFAULT NULL COMMENT '外部标准码（GB/T 2260 / ISO 4217）' AFTER i18n_key,
    ADD COLUMN effective_from DATETIME(3)     DEFAULT NULL COMMENT '生效开始时间' AFTER external_code,
    ADD COLUMN effective_to   DATETIME(3)     DEFAULT NULL COMMENT '生效结束时间' AFTER effective_from;

ALTER TABLE sys_dict_type
    ADD COLUMN value_type     VARCHAR(20)     NOT NULL DEFAULT 'STRING' COMMENT '值类型（STRING/INT/DECIMAL/BOOLEAN）' AFTER max_depth,
    ADD COLUMN value_regex    VARCHAR(255)    DEFAULT NULL COMMENT '值格式正则' AFTER value_type,
    ADD COLUMN code_segments  VARCHAR(64)     DEFAULT NULL COMMENT '层级编码分段，如 2,2,2' AFTER value_regex;

CREATE TABLE IF NOT EXISTS sys_dict_hierarchy (
    id                  BIGINT          NOT NULL COMMENT '主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    type_code           VARCHAR(64)     NOT NULL COMMENT '所属字典类型编码',
    hierarchy_code      VARCHAR(64)     NOT NULL DEFAULT 'DEFAULT' COMMENT '层级视图编码',
    code                VARCHAR(100)    NOT NULL COMMENT '子节点编码',
    parent_code         VARCHAR(100)    DEFAULT NULL COMMENT '父节点编码（NULL=根）',
    path                VARCHAR(512)    NOT NULL COMMENT '物化路径 /GD/GZ/',
    level               INT             NOT NULL DEFAULT 1 COMMENT '深度（根=1）',
    sort                INT             NOT NULL DEFAULT 0 COMMENT '同级排序',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_hierarchy (tenant_id, type_code, hierarchy_code, code),
    KEY idx_dict_hierarchy_parent (tenant_id, type_code, hierarchy_code, parent_code),
    KEY idx_dict_hierarchy_path (tenant_id, type_code, hierarchy_code, path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典层级关系';

CREATE TABLE IF NOT EXISTS sys_dict_item_text (
    id                  BIGINT          NOT NULL COMMENT '主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    type_code           VARCHAR(64)     NOT NULL COMMENT '所属字典类型编码',
    code                VARCHAR(100)    NOT NULL COMMENT '字典项编码',
    language            VARCHAR(16)     NOT NULL COMMENT '语言标签',
    label               VARCHAR(100)    NOT NULL COMMENT '该语言下的显示名',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '该语言下的说明',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item_text (tenant_id, type_code, code, language),
    KEY idx_dict_item_text_lang (tenant_id, type_code, language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项多语言译文';

-- ---------- 阶段 2：搬迁 parent_code → sys_dict_hierarchy ----------
-- 一级父指针：path = /父code/自身code/，level = 2；顶层项 level = 1，path = /自身code/。
-- 幂等：已存在同 (tenant_id, type_code, hierarchy_code, code) 的层级行不重复插入。
SET @h_base = (SELECT IFNULL(MAX(id), 0) FROM sys_dict_hierarchy);

INSERT INTO sys_dict_hierarchy (
    id, tenant_id, type_code, hierarchy_code, code, parent_code, path, level, sort,
    created_at, updated_at, deleted, version
)
SELECT
    @h_base := @h_base + 1,
    i.tenant_id,
    i.type_code,
    'DEFAULT',
    i.code,
    i.parent_code,
    CONCAT('/', COALESCE(i.parent_code, ''), IF(i.parent_code IS NULL, '', '/'), i.code, '/'),
    IF(i.parent_code IS NULL, 1, 2),
    COALESCE(i.sort, 0),
    i.created_at,
    i.updated_at,
    0,
    0
FROM sys_dict_item i
WHERE i.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_hierarchy h
      WHERE h.tenant_id = i.tenant_id
        AND h.type_code = i.type_code
        AND h.hierarchy_code = 'DEFAULT'
        AND h.code = i.code
  );

-- ---------- 阶段 3：Contract（v3 不再在项表存父指针） ----------
-- 确认阶段 2 数据无误后再执行；保留 parent_code 列不影响功能（代码已不读写它）。
-- ALTER TABLE sys_dict_item DROP COLUMN parent_code;
-- ALTER TABLE sys_dict_item DROP INDEX idx_dict_item_parent;
-- ALTER TABLE sys_dict_item ADD KEY idx_dict_item_type (tenant_id, type_code, sort);

-- ---------- 阶段 4：校验 ----------
-- SELECT (SELECT COUNT(*) FROM sys_dict_item WHERE deleted = 0 AND parent_code IS NOT NULL) AS v2_with_parent,
--        (SELECT COUNT(*) FROM sys_dict_hierarchy WHERE hierarchy_code = 'DEFAULT')          AS v3_hierarchy_rows;
-- 期望：v3_hierarchy_rows >= v2_with_parent。

-- ---------- 回滚 ----------
-- DROP TABLE IF EXISTS sys_dict_item_text;
-- DROP TABLE IF EXISTS sys_dict_hierarchy;
-- ALTER TABLE sys_dict_item  DROP COLUMN external_code, DROP COLUMN effective_from, DROP COLUMN effective_to;
-- ALTER TABLE sys_dict_type  DROP COLUMN value_type, DROP COLUMN value_regex, DROP COLUMN code_segments;
-- 如需恢复 v2 父指针：UPDATE sys_dict_item i JOIN sys_dict_hierarchy h
--   ON h.tenant_id=i.tenant_id AND h.type_code=i.type_code AND h.code=i.code AND h.hierarchy_code='DEFAULT'
--   SET i.parent_code = h.parent_code;
