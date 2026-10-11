-- =============================================================================
-- 0037_runtime_expense_l1_relations.sql
--
-- 目的（PRD v0.46 · G10 L1 落地）：把费用报销场景从「L0 单实体」升级为「L1 头+明细+类别主数据+附件」
--       —— 真实费用报销的必由形态：一张报销单含多笔消费明细、明细挂消费类别（主数据）、单头可带附件。
--       运行时关系引擎（RuntimeRelationSupport，REFERENCE / CHILD_TABLE / ATTACHMENT 三类型，零 DDL）
--       已实现，本脚本补齐场景实例化：物理表 + 运行时实体/字段种子 + 关系声明 + 权限。
--
-- 关联
--   · 0031_scenario_template_expense.sql：模板层（L0 形态，category 仍为 ENUM 码表）
--   · 0032_runtime_action_spi.sql：报销动作定义（submit/approve/reject/confirmPayout，已挂 expense_claim）
--   · 0033/0034_runtime_data_safety/permission_grant.sql：实体级+动作级权限（已挂 expense_claim）
--   · 前置：0032/0033/0035 已执行（本脚本引用 meta_entity_action_def / iam_permission 结构）
--
-- 设计要点
-- ----------------------
-- · 三张物理表：t_expense_claim（主实体，补建——此前仅被动作/权限引用，无建表脚本）、
--   t_expense_claim_item（明细子表）、t_expense_category（类别主数据）。
-- · 运行时实体种子：meta_entity（delivery_mode=1 RUNTIME、status=1 PUBLISHED）+ meta_field 全量，
--   category 字段从 ENUM 码表演进为 REFERENCE → t_expense_category（case §3「ENUM 类目迁主数据」）。
-- · 关系声明双轨：运行时引擎读 meta_field.validation_rules 内嵌 JSON（零 DDL）；
--   建模期登记 meta_entity_relation（设计视图/文档口径，与运行时同构不冲突）。
-- · 权限：新增 runtime:expense_claim_item:read/write、runtime:expense_category:read/write 四条细码，
--   授 SUPER_ADMIN（通配已覆盖，NOT EXISTS 防重）与 demo 管理员（role 3，仅读类别+明细读写）。
-- · 幂等：实体按 (tenant_id, code) uk、字段按 (entity_id, code) uk 做 ON DUPLICATE KEY UPDATE；
--   物理表 CREATE TABLE IF NOT EXISTS；权限 INSERT ... SELECT NOT EXISTS。可重复执行。
--
-- 执行（不在此脚本内执行，属 L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0037_runtime_expense_l1_relations.sql
-- =============================================================================

USE bone;

-- ── 0. 依赖护栏：前置迁移未执行则直接报错 ──────────────────────────────
SELECT id FROM meta_entity_action_def LIMIT 0;
SELECT id FROM iam_permission LIMIT 0;

-- ── 1. 物理表（主实体补建 + 明细 + 类别主数据） ─────────────────────────
CREATE TABLE IF NOT EXISTS t_expense_claim (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    claim_no            VARCHAR(32)     NOT NULL COMMENT '报销单号（L0 模板同构）',
    title               VARCHAR(200)    NOT NULL COMMENT '报销事由',
    amount              DECIMAL(20,6)   NOT NULL COMMENT '报销金额（G20 精度路线未动，演示口径诚实化注记）',
    category_id         BIGINT          DEFAULT NULL COMMENT '消费类别（REFERENCE → t_expense_category，L1 演进替代 ENUM 码表）',
    expense_date        DATE            DEFAULT NULL COMMENT '消费日期',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/SUBMITTED/APPROVED/REJECTED/PAID（0032 状态机）',
    applicant           VARCHAR(64)     DEFAULT NULL COMMENT '申请人',
    remark              VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    cost_center         VARCHAR(64)     DEFAULT NULL COMMENT '成本中心',
    attachment          TEXT            DEFAULT NULL COMMENT '附件清单（ATTACHMENT 关系，JSON 数组串：[{"name","url","size"}]）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_claim_tenant_no (tenant_id, claim_no, deleted),
    KEY idx_expense_claim_tenant_status (tenant_id, status),
    KEY idx_expense_claim_tenant_category (tenant_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费用报销单（L1：头，1—N 明细，REFERENCE 类别，ATTACHMENT 附件）';

CREATE TABLE IF NOT EXISTS t_expense_claim_item (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    claim_id            BIGINT          NOT NULL COMMENT '所属报销单ID（CHILD_TABLE 外键 → t_expense_claim.id）',
    sort_order          INT             DEFAULT NULL COMMENT '行序（CHILD_TABLE orderColumn）',
    note                VARCHAR(255)    NOT NULL COMMENT '消费说明',
    amount              DECIMAL(20,6)   NOT NULL COMMENT '本行金额（服务端强制 min:0.01，RC-007）',
    category_id         BIGINT          DEFAULT NULL COMMENT '消费类别（REFERENCE → t_expense_category）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_expense_item_tenant_claim (tenant_id, claim_id, deleted),
    KEY idx_expense_item_tenant_category (tenant_id, category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费用报销单明细行（1—N，cascadeDelete）';

CREATE TABLE IF NOT EXISTS t_expense_category (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    code                VARCHAR(64)     NOT NULL COMMENT '类别编码',
    name                VARCHAR(100)    NOT NULL COMMENT '类别名称（REFERENCE labelField）',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_expense_category_tenant_code (tenant_id, code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='费用报销类别主数据（REFERENCE 目标实体）';

-- ── 2. 类别主数据初始行（幂等：按 uk 更新） ─────────────────────────────
INSERT INTO t_expense_category
    (id, tenant_id, code, name, enabled, sort_order, version, created_at, updated_at, deleted)
VALUES
    (101, 1001, 'TRAVEL',     '差旅',       1, 1, 0, NOW(3), NOW(3), 0),
    (102, 1001, 'MEAL',       '餐饮',       1, 2, 0, NOW(3), NOW(3), 0),
    (103, 1001, 'OFFICE',     '办公耗材',   1, 3, 0, NOW(3), NOW(3), 0),
    (104, 1001, 'COMMUNICATION', '通讯',    1, 4, 0, NOW(3), NOW(3), 0),
    (105, 1001, 'OTHER',      '其他',       1, 5, 0, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), enabled = VALUES(enabled), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- ── 3. 运行时实体种子（delivery_mode=1 RUNTIME、status=1 PUBLISHED） ────
-- 注：expense_claim 此前无 meta_entity 种子（0032/0033 仅挂动作与权限），本次一并补齐；
--     已存在（手工实例化过）则按 uk 更新关键列，不重复建。
INSERT INTO meta_entity
    (id, tenant_id, name, code, display_name, description, table_name, type, delivery_mode, status,
     module_id, scope, template_id, is_builtin, icon, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (9210, 1001, '费用报销单', 'expense_claim', '费用报销',
     'L1 运行时实体：报销头（REFERENCE 类别 + CHILD_TABLE 明细 + ATTACHMENT 附件），状态机 submit/approve/reject/confirmPayout',
     't_expense_claim', 0, 1, 1, 58, 'TENANT', 9150, 0, 'expense', 10, 1, 1, NOW(3), NOW(3), 0, 0),
    (9220, 1001, '费用报销类别', 'expense_category', '报销类别',
     'L1 类别主数据（REFERENCE 目标实体）：差旅/餐饮/办公耗材/通讯/其他',
     't_expense_category', 1, 1, 1, 58, 'TENANT', NULL, 0, 'category', 20, 1, 1, NOW(3), NOW(3), 0, 0),
    (9230, 1001, '报销明细行', 'expense_claim_item', '报销明细',
     'L1 明细子实体：claim_id 外键（CHILD_TABLE）、category_id REFERENCE、amount 服务端强制 min:0.01',
     't_expense_claim_item', 0, 1, 1, 58, 'TENANT', NULL, 0, 'list', 30, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), description = VALUES(description),
    delivery_mode = VALUES(delivery_mode), status = VALUES(status), updated_at = NOW(3);

-- ── 4. 运行时字段种子 ───────────────────────────────────────────────────
-- 主实体 expense_claim（9210）：9 基础字段（L0 模板同构）+ category 演进 REFERENCE + attachment + items
INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92101, 1001, 9210, 58, '报销单号',   'claim_no',     '报销单号',     'STRING',  32, NULL, 1, 1, 0, 1, NULL, NULL, NULL, '报销单号（唯一）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, NOW(3), NOW(3), 0, 0),
    (92102, 1001, 9210, 58, '报销事由',   'title',        '报销事由',     'STRING',  200, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '报销事由/标题', NULL, 0, NULL, NULL, NULL, NULL, NULL, 2, 1, 1, NOW(3), NOW(3), 0, 0),
    (92103, 1001, 9210, 58, '报销金额',   'amount',       '报销金额',     'DECIMAL', NULL, NULL, 1, 0, 0, 0, NULL, NULL, '{"min":0.01}', '报销总金额（服务端强制 > 0）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 3, 1, 1, NOW(3), NOW(3), 0, 0),
    (92104, 1001, 9210, 58, '消费类别',   'category_id',  '消费类别',     'REFERENCE', NULL, NULL, 0, 0, 0, 1, NULL, NULL, '{"reference":{"targetEntityCode":"expense_category","labelField":"name"}}', 'L1：ENUM 码表演进为类别主数据引用', NULL, 0, NULL, NULL, NULL, NULL, NULL, 4, 1, 1, NOW(3), NOW(3), 0, 0),
    (92105, 1001, 9210, 58, '消费日期',   'expense_date', '消费日期',     'DATE',    NULL, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '消费日期', NULL, 0, NULL, NULL, NULL, NULL, NULL, 5, 1, 1, NOW(3), NOW(3), 0, 0),
    (92106, 1001, 9210, 58, '状态',       'status',       '状态',         'ENUM',    NULL, NULL, 1, 0, 0, 1, 'DRAFT', '["DRAFT","SUBMITTED","APPROVED","REJECTED","PAID"]', NULL, '5 态状态机（0032 动作定义驱动）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 6, 1, 1, NOW(3), NOW(3), 0, 0),
    (92107, 1001, 9210, 58, '申请人',     'applicant',    '申请人',       'STRING',  64, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '申请人', NULL, 0, NULL, NULL, NULL, NULL, NULL, 7, 1, 1, NOW(3), NOW(3), 0, 0),
    (92108, 1001, 9210, 58, '备注',       'remark',       '备注',         'TEXT',    NULL, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '备注', NULL, 0, NULL, NULL, NULL, NULL, NULL, 8, 1, 1, NOW(3), NOW(3), 0, 0),
    (92109, 1001, 9210, 58, '成本中心',   'cost_center',  '成本中心',     'STRING',  64, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '成本中心', NULL, 0, NULL, NULL, NULL, NULL, NULL, 9, 1, 1, NOW(3), NOW(3), 0, 0),
    (92110, 1001, 9210, 58, '附件',       'attachment',   '附件',         'ATTACHMENT', NULL, NULL, 0, 0, 0, 0, NULL, NULL, '{"attachment":{"maxCount":10,"maxSizeMB":10,"accept":["image/*","application/pdf"]}}', 'L1：附件清单（JSON 数组串，TEXT 物理列）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 10, 1, 1, NOW(3), NOW(3), 0, 0),
    (92111, 1001, 9210, 58, '明细行',     'items',        '明细行',       'CHILD_TABLE', NULL, NULL, 0, 0, 0, 0, NULL, NULL, '{"childTable":{"childEntityCode":"expense_claim_item","foreignKey":"claim_id","orderColumn":"sort_order","cascadeDelete":true,"minRows":0,"maxRows":200}}', 'L1：1—N 明细行声明字段（无物理列，v0.46 对齐豁免）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 11, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    physical_column = VALUES(physical_column), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- 类别主数据 expense_category（9220）
INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92201, 1001, 9220, 58, '类别编码', 'code',     '类别编码', 'STRING',  64, NULL, 1, 1, 0, 1, NULL, NULL, NULL, '类别编码（唯一）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, NOW(3), NOW(3), 0, 0),
    (92202, 1001, 9220, 58, '类别名称', 'name',     '类别名称', 'STRING',  100, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '类别名称（REFERENCE labelField）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 2, 1, 1, NOW(3), NOW(3), 0, 0),
    (92203, 1001, 9220, 58, '是否启用', 'enabled',  '是否启用', 'BOOLEAN', NULL, NULL, 0, 0, 0, 0, '1', NULL, NULL, '停用后不再出现在下拉', NULL, 0, NULL, NULL, NULL, NULL, NULL, 3, 1, 1, NOW(3), NOW(3), 0, 0),
    (92204, 1001, 9220, 58, '排序',     'sort_order', '排序',   'INTEGER', NULL, NULL, 0, 0, 0, 0, '0', NULL, NULL, '下拉排序', NULL, 0, NULL, NULL, NULL, NULL, NULL, 4, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    physical_column = VALUES(physical_column), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- 明细子实体 expense_claim_item（9230）
INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92301, 1001, 9230, 58, '报销单ID', 'claim_id',   '报销单ID',   'LONG',    NULL, NULL, 1, 0, 0, 1, NULL, NULL, NULL, '外键 → t_expense_claim.id（CHILD_TABLE foreignKey）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, NOW(3), NOW(3), 0, 0),
    (92302, 1001, 9230, 58, '行序',     'sort_order', '行序',       'INTEGER', NULL, NULL, 0, 0, 0, 0, '0', NULL, NULL, '明细行序（CHILD_TABLE orderColumn）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 2, 1, 1, NOW(3), NOW(3), 0, 0),
    (92303, 1001, 9230, 58, '消费说明', 'note',       '消费说明',   'STRING',  255, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '本行消费说明', NULL, 0, NULL, NULL, NULL, NULL, NULL, 3, 1, 1, NOW(3), NOW(3), 0, 0),
    (92304, 1001, 9230, 58, '行金额',   'amount',     '行金额',     'DECIMAL', NULL, NULL, 1, 0, 0, 0, NULL, NULL, '{"min":0.01}', '本行金额（服务端强制 min:0.01，RC-007）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 4, 1, 1, NOW(3), NOW(3), 0, 0),
    (92305, 1001, 9230, 58, '消费类别', 'category_id', '消费类别',   'REFERENCE', NULL, NULL, 0, 0, 0, 1, NULL, NULL, '{"reference":{"targetEntityCode":"expense_category","labelField":"name"}}', '明细行消费类别', NULL, 0, NULL, NULL, NULL, NULL, NULL, 5, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    physical_column = VALUES(physical_column), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- ── 5. 建模期关系登记（meta_entity_relation，与运行时 validation_rules 同构） ──
INSERT INTO meta_entity_relation
    (id, tenant_id, name, source_entity_id, target_entity_id, type, source_field_id, target_field_id,
     foreign_key_field, is_required, cascade_type, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (9301, 1001, '报销单-明细行', 9210, 9230, 'OneToMany', 92111, NULL, 'claim_id', 0, 'SOFT_DELETE', 1, 1, NOW(3), NOW(3), 0, 0),
    (9302, 1001, '明细行-类别',   9230, 9220, 'ManyToOne', 92305, NULL, 'category_id', 0, NULL, 1, 1, NOW(3), NOW(3), 0, 0),
    (9303, 1001, '报销单-类别',   9210, 9220, 'ManyToOne', 92104, NULL, 'category_id', 0, NULL, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    source_entity_id = VALUES(source_entity_id), target_entity_id = VALUES(target_entity_id),
    type = VALUES(type), foreign_key_field = VALUES(foreign_key_field), updated_at = NOW(3);

-- ── 6. 权限：明细/类别子实体读写码 + 授权 ───────────────────────────────
INSERT INTO iam_permission
    (id, code, name, type, resource_type, resource_path, action, description, parent_id, sort_order, created_by, created_at, updated_at, deleted)
VALUES
    (9311, 'runtime:expense_claim_item:read',  '运行时-报销明细-读', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim_item/**', 'read',  '报销明细子实体运行时读权限', 58, 131, 1, NOW(3), NOW(3), 0),
    (9312, 'runtime:expense_claim_item:write', '运行时-报销明细-写', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_claim_item/**', 'write', '报销明细子实体运行时写权限', 58, 132, 1, NOW(3), NOW(3), 0),
    (9321, 'runtime:expense_category:read',    '运行时-报销类别-读', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_category/**', 'read',  '报销类别主数据运行时读权限', 58, 141, 1, NOW(3), NOW(3), 0),
    (9322, 'runtime:expense_category:write',   '运行时-报销类别-写', 'OPERATION', 'metadata', '/api/v1/runtime/entities/expense_category/**', 'write', '报销类别主数据运行时写权限', 58, 142, 1, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), resource_path = VALUES(resource_path), action = VALUES(action),
    description = VALUES(description), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- SUPER_ADMIN（role 1）：通配已覆盖全部，显式授 4 条子实体码（NOT EXISTS 防重）
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT v.id, 1, p.id, NOW(3)
  FROM (SELECT 9401 AS id, 'runtime:expense_claim_item:read' AS code
        UNION ALL SELECT 9402, 'runtime:expense_claim_item:write'
        UNION ALL SELECT 9403, 'runtime:expense_category:read'
        UNION ALL SELECT 9404, 'runtime:expense_category:write') v
  JOIN iam_permission p ON p.code = v.code AND p.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 1 AND rp.permission_id = p.id);

-- demo 管理员（role 3，tenant 1001）：类别只读（下拉来源）、明细读写（提交报销时带明细）
INSERT INTO iam_role_permission (id, role_id, permission_id, created_at)
SELECT v.id, 3, p.id, NOW(3)
  FROM (SELECT 9411 AS id, 'runtime:expense_claim_item:read' AS code
        UNION ALL SELECT 9412, 'runtime:expense_claim_item:write'
        UNION ALL SELECT 9413, 'runtime:expense_category:read') v
  JOIN iam_permission p ON p.code = v.code AND p.deleted = 0
 WHERE NOT EXISTS (SELECT 1 FROM iam_role_permission rp WHERE rp.role_id = 3 AND rp.permission_id = p.id);

-- ── 7. 校验 ───────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM information_schema.tables
     WHERE table_schema = DATABASE() AND table_name IN ('t_expense_claim','t_expense_claim_item','t_expense_category')) AS l1_tables,
  (SELECT COUNT(*) FROM meta_entity WHERE deleted = 0 AND code IN ('expense_claim','expense_claim_item','expense_category')) AS runtime_entities,
  (SELECT COUNT(*) FROM meta_field WHERE deleted = 0 AND entity_id IN (9210, 9220, 9230) AND type IN ('REFERENCE','CHILD_TABLE','ATTACHMENT')) AS relation_fields,
  (SELECT COUNT(*) FROM meta_entity_relation WHERE deleted = 0 AND id IN (9301, 9302, 9303)) AS modeled_relations,
  (SELECT COUNT(*) FROM iam_permission WHERE deleted = 0 AND code LIKE 'runtime:expense_claim_item:%' OR (deleted = 0 AND code LIKE 'runtime:expense_category:%')) AS l1_permission_count;
