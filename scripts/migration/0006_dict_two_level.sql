-- ============================================================
-- 0006_dict_two_level.sql
-- 字典 v1 扁平模型 → v2 两级模型（类型 + 项）
--   sys_dict（v1，单表，type 只是字符串）  已下线，保留仅供回滚
--   sys_dict_type  新增 字典类型（定义层：category/ENUM 绑定/内置/层级上限）
--   sys_dict_item  新增 字典项（值层：parent_code 级联/默认项/展示语义/租户覆盖）
-- 依据：doc/design/modules/7a. 数据字典模块详细设计方案.md
-- 前置：bone-init.sql 已含两张新表；本脚本负责存量数据搬迁（可重复执行，幂等）。
-- 执行前：必须备份（见「阶段 0」）。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
-- 安全约定：只用 CREATE TABLE IF NOT EXISTS，绝不可先 DROP 备份表（同 0001 教训）。
CREATE TABLE IF NOT EXISTS bak_sys_dict_0006 AS SELECT * FROM sys_dict;

-- ---------- 阶段 1：Expand（建表，兼容表结构尚未同步的环境） ----------
CREATE TABLE IF NOT EXISTS sys_dict_type (
    id                  BIGINT          NOT NULL COMMENT '类型主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台级，全租户可见）',
    code                VARCHAR(64)     NOT NULL COMMENT '值域编码，如 sys_status',
    name                VARCHAR(100)    NOT NULL COMMENT '值域名称',
    category            VARCHAR(20)     NOT NULL DEFAULT 'LIST' COMMENT '值域分类（ENUM/LIST/CASCADE）',
    module_code         VARCHAR(64)     DEFAULT NULL COMMENT '归属模块（system/masterdata/metadata...）',
    enum_class          VARCHAR(255)    DEFAULT NULL COMMENT '绑定的 Java 枚举全限定名（category=ENUM 必填）',
    max_depth           INT             NOT NULL DEFAULT 0 COMMENT 'CASCADE 层级上限（0=不限）',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '用途说明',
    builtin             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '平台内置（禁删、code 禁改）',
    editable            TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '租户是否可改其项',
    sort                INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status              INT             NOT NULL DEFAULT 1 COMMENT '状态（1=启用 0=禁用）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_type (tenant_id, code),
    KEY idx_dict_type_module (tenant_id, module_code, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型';

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id                  BIGINT          NOT NULL COMMENT '字典项主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（0=平台项；>0=租户覆盖项/自有项）',
    type_code           VARCHAR(64)     NOT NULL COMMENT '所属字典类型编码',
    parent_code         VARCHAR(100)    DEFAULT NULL COMMENT '父项编码（NULL=顶层，CASCADE 用）',
    code                VARCHAR(100)    NOT NULL COMMENT '项编码（ENUM 类即枚举常量名）',
    label               VARCHAR(100)    NOT NULL COMMENT '显示名',
    value               VARCHAR(255)    DEFAULT NULL COMMENT '业务值（可与 code 不同）',
    enum_name           VARCHAR(100)    DEFAULT NULL COMMENT '绑定枚举常量名（ENUM 类冗余，便于反查）',
    tag_type            VARCHAR(20)     NOT NULL DEFAULT 'default' COMMENT '展示语义（default/info/success/warning/error）',
    i18n_key            VARCHAR(128)    DEFAULT NULL COMMENT '文案国际化键（字典不存译文）',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否该类型默认项',
    sort                INT             NOT NULL DEFAULT 0 COMMENT '同级排序',
    status              INT             NOT NULL DEFAULT 1 COMMENT '状态（1=启用 0=禁用）',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dict_item (tenant_id, type_code, code),
    KEY idx_dict_item_parent (tenant_id, type_code, parent_code, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';

-- ---------- 阶段 2：搬迁 sys_dict → sys_dict_type ----------
-- 类型行由 (tenant_id, type) 聚合而来；type_name 取该组最早非空值作为类型名。
-- 幂等：已存在同 (tenant_id, code) 的类型不重复插入。
SET @dict_type_base = (SELECT IFNULL(MAX(id), 0) FROM sys_dict_type);

INSERT INTO sys_dict_type (
    id, tenant_id, code, name, category, module_code, enum_class, max_depth,
    description, builtin, editable, sort, status, created_at, updated_at, deleted, version
)
SELECT
    @dict_type_base := @dict_type_base + 1,
    d.tenant_id,
    d.type,
    COALESCE(MIN(NULLIF(TRIM(COALESCE(d.type_name, '')), '')), d.type),
    'LIST',
    'system',
    NULL,
    0,
    CONCAT('由 sys_dict 迁移（原 type=', d.type, '）'),
    0,
    1,
    0,
    1,
    MIN(d.created_at),
    MAX(d.updated_at),
    0,
    0
FROM sys_dict d
WHERE d.deleted = 0
  AND d.type IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_type t
      WHERE t.tenant_id = d.tenant_id AND t.code = d.type
  )
GROUP BY d.tenant_id, d.type;

-- ---------- 阶段 3：搬迁 sys_dict → sys_dict_item ----------
-- 直接沿用原行主键，保证外部已引用的 id 不漂移；parent_code 留空（v1 无层级语义）。
-- 幂等：已存在同 (tenant_id, type_code, code) 的项不重复插入。
INSERT INTO sys_dict_item (
    id, tenant_id, type_code, parent_code, code, label, value, enum_name,
    tag_type, i18n_key, is_default, sort, status, description,
    created_at, updated_at, deleted, version
)
SELECT
    d.id,
    d.tenant_id,
    d.type,
    NULL,
    d.code,
    COALESCE(d.label, d.code),
    d.value,
    NULL,
    'default',
    NULL,
    0,
    COALESCE(d.sort, 0),
    COALESCE(d.status, 1),
    NULL,
    d.created_at,
    d.updated_at,
    d.deleted,
    0
FROM sys_dict d
WHERE d.deleted = 0
  AND d.type IS NOT NULL
  AND d.code IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_dict_item i
      WHERE i.tenant_id = d.tenant_id AND i.type_code = d.type AND i.code = d.code
  );

-- ---------- 阶段 4：校验（人工确认后再决定何时 DROP sys_dict） ----------
-- SELECT (SELECT COUNT(*) FROM sys_dict WHERE deleted = 0)              AS v1_rows,
--        (SELECT COUNT(*) FROM sys_dict_type)                           AS v2_types,
--        (SELECT COUNT(*) FROM sys_dict_item)                           AS v2_items;
-- 期望：v2_items >= v1_rows 且 v2_types = sys_dict 中 distinct (tenant_id, type) 数量。

-- ---------- 回滚 ----------
-- v2 数据可安全丢弃（可从 bak_sys_dict_0006 重建）：
--   DROP TABLE IF EXISTS sys_dict_item;
--   DROP TABLE IF EXISTS sys_dict_type;
--   如需恢复 v1：INSERT INTO sys_dict SELECT * FROM bak_sys_dict_0006;（先清空 sys_dict）
