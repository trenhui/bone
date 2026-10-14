-- 0047_ui_schema_demo_seed.sql
-- P0-3 建模页两套 schema 种子【DEMO/DEV 联调专用，禁上生产】。
-- 前置：迁移 0001~0046（含 0046 meta_entity_ui_schema + 模板 ui 列）已执行；
--       0037 报销实体（expense_claim@租户按实体归属，默认 1001）已存在。
-- 内容：
--   §1 报销：entity 归属租户查 entity 动态取值（不假设 1001）；tenant0 4 动作复制到实体租户；
--       upsert meta_entity_ui_schema（entityCode=expense_claim，12 真实字段对齐版）。
--   §2 理赔：新建 claim_case@tenant0（9240）+ 19 字段 + 子实体 claim_case_item（9245）+ 5 字段；
--       物理表 t_claim_case / t_claim_case_item；6 动作；模板 outpatient_fasttrack（1.1.0 现行）；
--       历史快照 1.0.0；实例 base 指针（outpatient_fasttrack:1.0.0, notify）。
-- 幂等：全部 INSERT ... ON DUPLICATE KEY UPDATE / IF NOT EXISTS，可重复执行。
-- 回滚：DROP TABLE t_claim_case, t_claim_case_item；DELETE 三类种子行（见尾部注释）。

-- ── 0. 备份（与 0016 同模式） ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS bak_0047_uischema AS SELECT * FROM meta_entity_ui_schema WHERE 1 = 0;
INSERT INTO bak_0047_uischema SELECT * FROM meta_entity_ui_schema
WHERE (tenant_id, entity_id) IN ((0, 9240)) OR entity_id IN (SELECT id FROM meta_entity WHERE code = 'expense_claim');

-- ── 1. 报销（expense_claim）：实体归属租户动态取值 ────────────────────────
SET @exp_entity_id = (SELECT id FROM meta_entity WHERE code = 'expense_claim' AND deleted = 0 LIMIT 1);
SET @exp_tenant_id = (SELECT tenant_id FROM meta_entity WHERE code = 'expense_claim' AND deleted = 0 LIMIT 1);

-- 1.1 动作定义：tenant0 → 实体租户复制（缺啥补啥；ui-schema UI-REF-02 要求同租户可见）。
INSERT INTO meta_entity_action_def
  (tenant_id, entity_code, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
SELECT @exp_tenant_id, d.entity_code, d.action_code, d.label, d.from_status, d.to_status, d.status_field,
   d.is_escape, d.handler_bean, d.params_schema, d.permission_code, d.requires_idempotency, d.sort_order,
   NOW(3), NOW(3), 0, 0
FROM meta_entity_action_def d
WHERE d.tenant_id = 0 AND d.entity_code = 'expense_claim' AND d.deleted = 0
  AND @exp_tenant_id IS NOT NULL AND @exp_tenant_id <> 0
  AND NOT EXISTS (SELECT 1 FROM meta_entity_action_def x
    WHERE x.tenant_id = @exp_tenant_id AND x.entity_code = 'expense_claim'
      AND x.action_code = d.action_code AND x.deleted = 0);

-- 1.2 报销界面 schema（草稿；无 base 指针 → base-update 诚实 NO_BASE_POINTER）。
INSERT INTO meta_entity_ui_schema
  (tenant_id, entity_id, schema_json, schema_version, schema_minor,
   published_schema_json, published_version, base_template_code, base_version, follow_mode,
   lock_version, created_at, updated_at, deleted)
SELECT @exp_tenant_id, @exp_entity_id, '{"actionBar":[{"actionCode":"submit","sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"DRAFT"}},{"actionCode":"approve","sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"SUBMITTED"}},{"actionCode":"reject","sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"SUBMITTED"}},{"actionCode":"confirmPayout","sortOrder":4,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"APPROVED"}}],"displayModes":["create","edit","view"],"entityCode":"expense_claim","linkageRules":[{"code":"LINK-COST-01","name":"大额报销必须填写成本中心","then":[{"fieldCode":"cost_center","notice":"金额≥5000 元须填写成本中心（部门/项目归集）","required":true,"visible":true}],"when":{"fieldCode":"amount","operator":"gte","value":5000}},{"code":"LINK-APPR-01","name":"大额报销必须指定审批人","then":[{"fieldCode":"approver_id","required":true,"visible":true}],"when":{"fieldCode":"amount","operator":"gte","value":5000}}],"schemaVersion":1,"submitRules":[{"code":"SUB-SUM-01","compare":{"fieldCode":"amount","valueType":"field"},"fields":["items.amount"],"function":"sum","message":"费用明细合计必须等于申报金额","name":"明细合计一致","operator":"eq","verifyType":"ERROR"},{"code":"SUB-MIN-01","compare":{"value":0.01,"valueType":"fixed"},"fields":["items.amount"],"function":"min","message":"费用明细单行金额不得小于 0.01（与服务端 min:0.01 同值）","name":"单行金额下限","operator":"gte","verifyType":"ERROR"},{"code":"SUB-CNT-01","compare":{"value":1,"valueType":"fixed"},"fields":["items"],"function":"count","message":"费用明细至少保留一行","name":"明细至少一行","operator":"gte","verifyType":"ERROR"}],"views":{"detail":{"sections":[{"controls":[{"fieldCode":"title"},{"fieldCode":"category_id"},{"fieldCode":"amount"},{"fieldCode":"status"}],"id":"sec_base","title":"基本信息"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items"}],"id":"sec_items","title":"费用明细"}]},"form":{"sections":[{"controls":[{"controlKind":"INPUT","fieldCode":"title","placeholder":"如：10 月差旅报销"},{"controlKind":"REFERENCE","fieldCode":"category_id","props":{"labelField":"name","targetEntity":"expense_category","valueField":"id"}},{"controlKind":"INPUT","fieldCode":"applicant","readonly":true},{"controlKind":"DATE","fieldCode":"expense_date"}],"id":"sec_base","title":"基本信息"},{"controls":[{"controlKind":"NUMBER","fieldCode":"amount","props":{"min":0.01,"precision":2}},{"controlKind":"INPUT","fieldCode":"cost_center","placeholder":"如：研发部 / PROJECT-2026"},{"controlKind":"TEXTAREA","fieldCode":"remark","placeholder":"补充说明（选填）"},{"controlKind":"REFERENCE","fieldCode":"approver_id","props":{"labelField":"name","targetEntity":"sys_user","valueField":"id"}}],"id":"sec_amount","title":"金额与归属"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"expense_claim_item","columns":["note","amount","category_id"]}}],"id":"sec_items","title":"费用明细"},{"controls":[{"controlKind":"ATTACHMENT","fieldCode":"attachment","props":{"maxCount":10,"maxSizeMB":10}}],"id":"sec_files","title":"发票影像"},{"controls":[{"controlKind":"ENUM","fieldCode":"status","readonly":true}],"id":"sec_status","title":"状态"}]},"list":{"batchActions":["submit"],"columns":[{"fieldCode":"claim_no","visible":true,"width":140},{"fieldCode":"title","visible":true,"width":220},{"fieldCode":"amount","visible":true,"width":130},{"fieldCode":"category_id","visible":true,"width":120},{"fieldCode":"status","visible":true,"width":100},{"fieldCode":"applicant","visible":true,"width":100},{"fieldCode":"expense_date","visible":true,"width":120}],"dataScope":{"policyCode":"owner_or_approver"},"searchFields":["status","amount"],"tabs":[{"fieldCode":"approver_id","title":"待我审批","value":"$me"},{"fieldCode":"applicant","title":"我提交的","value":"$me"},{"fieldCode":"status","title":"全部","value":"$all"}]}}}', 1, 1,
   NULL, NULL, NULL, NULL, 'pinned',
   0, NOW(3), NOW(3), 0
WHERE @exp_entity_id IS NOT NULL
ON DUPLICATE KEY UPDATE schema_json = VALUES(schema_json), schema_minor = 1, updated_at = NOW(3);

-- ── 2. 理赔（claim_case@tenant0）：实体 + 字段 + 子表 + 物理表 ─────────────
INSERT INTO meta_entity
    (id, tenant_id, name, code, display_name, description, table_name, type, delivery_mode, status,
     module_id, scope, template_id, is_builtin, icon, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (9240, 0, '理赔案件', 'claim_case', '理赔',
     'P0-3 演示实体：门诊快赔（6 态状态机 + CHILD_TABLE 明细 + ATTACHMENT 证据 + 快赔入口），0047 种子',
     't_claim_case', 0, 1, 1, NULL, 'TENANT', NULL, 0, 'claim', 40, 1, 1, NOW(3), NOW(3), 0, 0),
    (9245, 0, '理赔费用明细', 'claim_case_item', '费用明细',
     'P0-3 演示子实体：claim_id 外键（CHILD_TABLE），0047 种子',
     't_claim_case_item', 0, 1, 1, NULL, 'TENANT', NULL, 0, 'list', 41, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), description = VALUES(description),
    delivery_mode = VALUES(delivery_mode), status = VALUES(status), updated_at = NOW(3);

-- 2.1 行策略（reviewer_or_owner；引擎对象形态，ui-schema 解析器已兼容 0038 形态）。
UPDATE meta_entity
SET row_policies = '{"owner_field":"created_by","rules":[{"name":"reviewer_or_owner","effect":"allow","when":"reviewer_id = :operator OR created_by = :operator"}]}',
    updated_by = 1, updated_at = NOW(3)
WHERE tenant_id = 0 AND code = 'claim_case';

INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92401, 0, 9240, NULL, 'claimNo', 'claim_no', '报案号', 'STRING', 64, NULL, 1, 1, 0, 0, NULL, NULL, NULL, '业务唯一编码', NULL, 0, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, NOW(3), NOW(3), 0, 0),
    (92402, 0, 9240, NULL, 'policyNo', 'policy_no', '保单号', 'STRING', 64, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '承保保单号', NULL, 0, NULL, NULL, NULL, NULL, NULL, 2, 1, 1, NOW(3), NOW(3), 0, 0),
    (92403, 0, 9240, NULL, 'insuredName', 'insured_name', '被保人', 'STRING', 64, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '被保险人姓名', NULL, 0, NULL, NULL, NULL, NULL, NULL, 3, 1, 1, NOW(3), NOW(3), 0, 0),
    (92404, 0, 9240, NULL, 'insuredIdHash', 'insured_id_hash', '被保人ID哈希', 'STRING', 128, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '脱敏展示（mask=hash）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 4, 1, 1, NOW(3), NOW(3), 0, 0),
    (92405, 0, 9240, NULL, 'claimType', 'claim_type', '理赔类型', 'ENUM', NULL, NULL, 1, 0, 0, 0, NULL, '["OUTPATIENT","INPATIENT","DENTAL"]', NULL, '门诊/住院/齿科', NULL, 0, NULL, NULL, NULL, NULL, NULL, 5, 1, 1, NOW(3), NOW(3), 0, 0),
    (92406, 0, 9240, NULL, 'hospitalLevel', 'hospital_level', '医院等级', 'ENUM', NULL, NULL, 1, 0, 0, 0, NULL, '["L1","L2","L3"]', NULL, '一/二/三级', NULL, 0, NULL, NULL, NULL, NULL, NULL, 6, 1, 1, NOW(3), NOW(3), 0, 0),
    (92407, 0, 9240, NULL, 'invoiceNo', 'invoice_no', '发票号', 'STRING', 64, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '就诊发票号', NULL, 0, NULL, NULL, NULL, NULL, NULL, 7, 1, 1, NOW(3), NOW(3), 0, 0),
    (92408, 0, 9240, NULL, 'invoiceDate', 'invoice_date', '开票日期', 'DATE', NULL, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '发票开具日期', NULL, 0, NULL, NULL, NULL, NULL, NULL, 8, 1, 1, NOW(3), NOW(3), 0, 0),
    (92409, 0, 9240, NULL, 'claimAmount', 'claim_amount', '申请金额', 'DECIMAL', NULL, NULL, 1, 0, 0, 0, NULL, NULL, '{"min":0.01}', '申请金额（元）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 9, 1, 1, NOW(3), NOW(3), 0, 0),
    (92410, 0, 9240, NULL, 'payableAmount', 'payable_amount', '应赔金额', 'DECIMAL', NULL, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '规则引擎计算，后端为准（COMPUTED 展示）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 10, 1, 1, NOW(3), NOW(3), 0, 0),
    (92411, 0, 9240, NULL, 'reviewerId', 'reviewer_id', '审核人', 'LONG', NULL, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '待我审核 tab 依赖', NULL, 0, NULL, NULL, NULL, NULL, NULL, 11, 1, 1, NOW(3), NOW(3), 0, 0),
    (92412, 0, 9240, NULL, 'secondReviewer', 'second_reviewer', '复核人', 'LONG', NULL, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '大额双人复核（LINK-DUAL-01）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 12, 1, 1, NOW(3), NOW(3), 0, 0),
    (92413, 0, 9240, NULL, 'fraudFlag', 'fraud_flag', '风险标记', 'ENUM', NULL, NULL, 0, 0, 0, 0, 'CLEAR', '["CLEAR","REVIEW","FRAUD"]', NULL, '风险标记 tab 依赖', NULL, 0, NULL, NULL, NULL, NULL, NULL, 13, 1, 1, NOW(3), NOW(3), 0, 0),
    (92414, 0, 9240, NULL, 'fraudFlags', 'fraud_flags', '风险标签', 'STRING', 256, NULL, 0, 0, 0, 0, NULL, NULL, NULL, 'ALERT 展示（命中 R-FRAUD 转人工）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 14, 1, 1, NOW(3), NOW(3), 0, 0),
    (92415, 0, 9240, NULL, 'capReached', 'cap_reached', '已达封顶', 'BOOLEAN', NULL, NULL, 0, 0, 0, 0, '0', NULL, NULL, '封顶 tab 依赖', NULL, 0, NULL, NULL, NULL, NULL, NULL, 15, 1, 1, NOW(3), NOW(3), 0, 0),
    (92416, 0, 9240, NULL, 'coverageClear', 'coverage_clear', '保障清晰', 'BOOLEAN', NULL, NULL, 0, 0, 0, 0, '0', NULL, NULL, '快赔四条件之一（LINK-FAST-01）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 16, 1, 1, NOW(3), NOW(3), 0, 0),
    (92417, 0, 9240, NULL, 'evidences', 'evidences', '材料证据', 'ATTACHMENT', NULL, NULL, 0, 0, 0, 0, NULL, NULL, '{"attachment":{"maxCount":20,"maxSizeMB":10}}', '材料证据（JSON 数组串）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 17, 1, 1, NOW(3), NOW(3), 0, 0),
    (92418, 0, 9240, NULL, 'items', 'items', '费用明细', 'CHILD_TABLE', NULL, NULL, 0, 0, 0, 0, NULL, NULL, '{"childTable":{"childEntityCode":"claim_case_item","foreignKey":"claim_id","orderColumn":"sort_order","cascadeDelete":true,"minRows":0,"maxRows":200}}', '1—N 费用明细（无物理列）', NULL, 0, NULL, NULL, NULL, NULL, NULL, 18, 1, 1, NOW(3), NOW(3), 0, 0),
    (92419, 0, 9240, NULL, 'status', 'status', '状态', 'ENUM', NULL, NULL, 1, 0, 0, 0, 'DRAFT', '["DRAFT","REVIEWING","APPROVED","REJECTED","PAID"]', NULL, '6 态理赔状态机', NULL, 0, NULL, NULL, NULL, NULL, NULL, 19, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    sort_order = VALUES(sort_order), updated_at = NOW(3);

INSERT INTO meta_field
    (id, tenant_id, entity_id, module_id, name, code, display_name, type, length, numeric_precision,
     is_required, is_unique, is_pk, is_indexed, default_value, enum_values, validation_rules, comment,
     data_classification, is_pii, sensitivity_level, data_steward, business_term, source_system,
     physical_column, sort_order, created_by, updated_by, created_at, updated_at, deleted, version)
VALUES
    (92451, 0, 9245, NULL, 'claimId', 'claim_id', '案件ID', 'LONG', NULL, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '外键 → t_claim_case.id', NULL, 0, NULL, NULL, NULL, NULL, NULL, 1, 1, 1, NOW(3), NOW(3), 0, 0),
    (92452, 0, 9245, NULL, 'sortOrder', 'sort_order', '行序', 'INTEGER', NULL, NULL, 0, 0, 0, 0, '0', NULL, NULL, '明细行序', NULL, 0, NULL, NULL, NULL, NULL, NULL, 2, 1, 1, NOW(3), NOW(3), 0, 0),
    (92453, 0, 9245, NULL, 'name', 'name', '费用名称', 'STRING', 128, NULL, 1, 0, 0, 0, NULL, NULL, NULL, '费用项目名称', NULL, 0, NULL, NULL, NULL, NULL, NULL, 3, 1, 1, NOW(3), NOW(3), 0, 0),
    (92454, 0, 9245, NULL, 'amount', 'amount', '行金额', 'DECIMAL', NULL, NULL, 1, 0, 0, 0, NULL, NULL, '{"min":0.01}', '本行金额', NULL, 0, NULL, NULL, NULL, NULL, NULL, 4, 1, 1, NOW(3), NOW(3), 0, 0),
    (92455, 0, 9245, NULL, 'category', 'category', '费用类别', 'STRING', 64, NULL, 0, 0, 0, 0, NULL, NULL, NULL, '费用类别', NULL, 0, NULL, NULL, NULL, NULL, NULL, 5, 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), type = VALUES(type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), comment = VALUES(comment),
    sort_order = VALUES(sort_order), updated_at = NOW(3);

-- 2.2 物理表（惯例同 0037 §1：id/tenant_id/version/审计/deleted；items 无物理列，走子表）。
CREATE TABLE IF NOT EXISTS t_claim_case (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    claim_no            VARCHAR(64)     NOT NULL COMMENT '报案号（唯一）',
    policy_no           VARCHAR(64)     NOT NULL COMMENT '保单号',
    insured_name        VARCHAR(64)     NOT NULL COMMENT '被保人',
    insured_id_hash     VARCHAR(128)    DEFAULT NULL COMMENT '被保人ID哈希',
    claim_type          VARCHAR(32)     NOT NULL COMMENT 'OUTPATIENT/INPATIENT/DENTAL',
    hospital_level      VARCHAR(8)      NOT NULL COMMENT 'L1/L2/L3',
    invoice_no          VARCHAR(64)     NOT NULL COMMENT '发票号',
    invoice_date        DATE            DEFAULT NULL COMMENT '开票日期',
    claim_amount        DECIMAL(20,2)   NOT NULL COMMENT '申请金额',
    payable_amount      DECIMAL(20,2)   DEFAULT NULL COMMENT '应赔金额（规则引擎计算）',
    reviewer_id         BIGINT          DEFAULT NULL COMMENT '审核人',
    second_reviewer     BIGINT          DEFAULT NULL COMMENT '复核人（大额双人）',
    fraud_flag          VARCHAR(16)     DEFAULT 'CLEAR' COMMENT 'CLEAR/REVIEW/FRAUD',
    fraud_flags         VARCHAR(256)    DEFAULT NULL COMMENT '风险标签',
    cap_reached         TINYINT(1)      DEFAULT 0 COMMENT '已达封顶',
    coverage_clear      TINYINT(1)      DEFAULT 0 COMMENT '保障清晰（快赔条件）',
    evidences           TEXT            DEFAULT NULL COMMENT '材料证据（JSON 数组串）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/REVIEWING/APPROVED/REJECTED/PAID',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_case_tenant_no (tenant_id, claim_no, deleted),
    KEY idx_claim_case_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='理赔案件（P0-3 演示：门诊快赔，0047 种子）';

CREATE TABLE IF NOT EXISTS t_claim_case_item (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    claim_id            BIGINT          NOT NULL COMMENT '所属案件ID（CHILD_TABLE 外键 → t_claim_case.id）',
    sort_order          INT             DEFAULT 0 COMMENT '行序',
    name                VARCHAR(128)    NOT NULL COMMENT '费用名称',
    amount              DECIMAL(20,2)   NOT NULL COMMENT '行金额',
    category            VARCHAR(64)     DEFAULT NULL COMMENT '费用类别',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_claim_item_claim (claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='理赔费用明细（0047 种子）';

-- 2.3 理赔动作定义（tenant0；submit/approve 双人复核 requireDualApprove 走 schema 门控，动作行只做状态机）。
INSERT INTO meta_entity_action_def
  (tenant_id, entity_code, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, permission_code, requires_idempotency, sort_order,
   created_at, updated_at, deleted, version)
VALUES
    (0, 'claim_case', 'submit', '提交', 'DRAFT', 'REVIEWING', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 0, 10, NOW(3), NOW(3), 0, 0),
    (0, 'claim_case', 'approve', '审核通过', 'REVIEWING', 'APPROVED', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 0, 20, NOW(3), NOW(3), 0, 0),
    (0, 'claim_case', 'reject', '拒赔', 'REVIEWING', 'REJECTED', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 0, 21, NOW(3), NOW(3), 0, 0),
    (0, 'claim_case', 'requestMoreEvidence', '补件', 'REVIEWING', 'REVIEWING', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 0, 22, NOW(3), NOW(3), 0, 0),
    (0, 'claim_case', 'pay', '支付', 'APPROVED', 'PAID', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 1, 30, NOW(3), NOW(3), 0, 0),
    (0, 'claim_case', 'appeal', '申诉', 'REJECTED', 'REVIEWING', 'status',
     0, NULL, NULL, 'metadata:runtime:write', 0, 40, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
    requires_idempotency = VALUES(requires_idempotency), updated_at = NOW(3);

-- 2.4 模板资产 outpatient_fasttrack（现行 1.1.0 = 实例 + 回执卡段；归并演示 sectionAdded=1）。
INSERT INTO meta_model_template
    (id, tenant_id, code, name, description, category, current_version, status,
     ui_schema_version, ui_schema_json, created_by, created_at, updated_at, deleted, version)
VALUES
    (9160, 0, 'outpatient_fasttrack', '门诊快赔',
     'P0-3 演示模板：门诊快赔界面资产（现行 1.1.0），0047 种子', '理赔', 'v1.0.0', 1,
     '1.1.0', '{"actionBar":[{"actionCode":"submit","sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"DRAFT"}},{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":4,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"pay","confirm":{"content":"支付经资金两段式（硬预占→已支付），幂等键=报案号","contentKey":"confirm.pay.content","title":"确认支付","titleKey":"confirm.pay.title"},"sortOrder":5,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"APPROVED"}},{"actionCode":"appeal","sortOrder":6,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REJECTED"}}],"base":{"baseVersion":"1.0.0","followMode":"notify","templateCode":"outpatient_fasttrack"},"displayModes":["create","edit","view"],"entityCode":"claim_case","footer":{"aiBadge":true,"disclaimer":"拒赔结论附条款原文与证据位置；申诉 48h 响应","disclaimerKey":"footer.disclaimer"},"i18n":{"namespace":"bone.ui.claim_case"},"linkageRules":[{"code":"LINK-DUAL-01","name":"大额强制双人复核提示","then":[{"fieldCode":"second_reviewer","notice":"金额≥1 万元须双人复核（R-MAN-01）","noticeKey":"notice.dual_review","required":true,"visible":true}],"when":{"fieldCode":"claim_amount","operator":"gte","value":10000}},{"code":"LINK-EVID-01","name":"材料缺失转补件提示","then":[{"fieldCode":"evidences","notice":"材料缺失请补件，不阻塞其他案件（R-DEC P5）","required":true}],"when":{"fieldCode":"evidences","operator":"isNull"}},{"code":"LINK-FAST-01","name":"快赔资格四条件全满足才亮快赔入口（R-FAST-01，all 语义禁拆分）","then":[{"fieldCode":"fasttrack_entry","visible":true}],"when":{"all":[{"fieldCode":"calc_amount","operator":"lte","value":500},{"fieldCode":"evidences","operator":"isNotNull"},{"fieldCode":"fraud_flags","operator":"isNull"},{"fieldCode":"coverage_clear","operator":"eq","value":true}]}}],"schemaVersion":1,"submitRules":[{"code":"SUB-CONS-01","compare":{"fieldCode":"claim_amount","valueType":"field"},"fields":["claim_amount","items.amount"],"function":"consistency","message":"发票金额与清单合计不一致（差额>1 元，R-FRAUD-04）","messageKey":"submit.consistency","name":"发票与清单一致","operator":"eq","params":{"tolerance":1},"verifyType":"ERROR"},{"code":"SUB-COMP-01","fields":["policy_no","insured_name","claim_type","hospital_level","invoice_no","claim_amount"],"function":"completeness","message":"申请要素须完整（保单/被保人/类型/医院等级/发票号/金额 6 件套，R-MAN-03）","messageKey":"submit.reject_reasons","name":"申请要素完整性","operator":"gte","params":{"minReasons":6},"verifyType":"ERROR"}],"variants":[{"actionBar":[{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}}],"key":"reviewer","label":"核赔工作台","match":{"roles":["reviewer","admin"]},"priority":10,"views":{"detail":{"sections":[{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"}]},"form":{"sections":[{"controls":[{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}}],"id":"sec_calc","kind":"dashboard","title":"理算与风险（先看结论）"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]},"readonly":true}],"id":"sec_items","title":"费用明细（只读核对）"}]}}}],"views":{"form":{"sections":[{"controls":[{"controlKind":"INPUT","fieldCode":"policy_no"},{"controlKind":"INPUT","fieldCode":"insured_id_hash","props":{"mask":"hash"},"readonly":true},{"controlKind":"ENUM","fieldCode":"claim_type"},{"controlKind":"ENUM","fieldCode":"hospital_level"},{"controlKind":"INPUT","fieldCode":"invoice_no"},{"controlKind":"DATE","fieldCode":"invoice_date"}],"id":"sec_apply","title":"申请信息（员工端）","titleKey":"sections.sec_apply"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]}}],"id":"sec_items","title":"费用明细","titleKey":"sections.sec_items"},{"controls":[{"controlKind":"ATTACHMENT","fieldCode":"evidences","props":{"maxCount":20,"maxSizeMB":10}}],"id":"sec_evidences","title":"材料证据","titleKey":"sections.sec_evidences"},{"controls":[{"controlKind":"STAT","fieldCode":"deductible_used","props":{"lineage":"deductible-annual-cumu"},"virtual":true},{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}},{"controlKind":"NUMBER","fieldCode":"calc_amount","readonly":true,"virtual":true},{"controlKind":"SWITCH","fieldCode":"coverage_clear","readonly":true},{"controlKind":"SWITCH","fieldCode":"fasttrack_entry","readonly":true,"virtual":true}],"id":"sec_calc","kind":"dashboard","title":"理算引用（只读：规则引擎计算，后端为准）","titleKey":"sections.sec_calc"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹","titleKey":"sections.sec_trace"},{"id":"sec_tpl_notice","title":"模板更新说明（1.1.0 新增回执卡）","controls":[{"fieldCode":"status","controlKind":"ENUM","readonly":true,"notice":"1.1.0 新增：终态自动短信回执（跟随演示段）"}]}]},"list":{"columns":[{"fieldCode":"claim_no","visible":true,"width":150},{"fieldCode":"insured_name","visible":true,"width":100},{"fieldCode":"hospital_level","visible":true,"width":110},{"fieldCode":"claim_amount","visible":true,"width":120},{"fieldCode":"payable_amount","visible":true,"width":120},{"fieldCode":"status","visible":true,"width":120}],"dataScope":{"policyCode":"reviewer_or_owner"},"searchFields":["status","hospital_level","claim_amount"],"stats":[{"title":"今日待办","titleKey":"stats.todo_today","valueRef":"todo_today"},{"title":"快赔自动通过","valueRef":"fasttrack_rate"},{"title":"转人工率","valueRef":"manual_rate"}],"tabs":[{"fieldCode":"reviewer_id","title":"待我审核","titleKey":"tabs.todo","value":"$me"},{"fieldCode":"fraud_flag","title":"风险标记","value":true},{"fieldCode":"cap_reached","title":"已达封顶","value":true}]}}}', 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE ui_schema_version = VALUES(ui_schema_version), ui_schema_json = VALUES(ui_schema_json),
    updated_at = NOW(3);

INSERT INTO meta_model_template_ui_history (tenant_id, template_code, version, schema_json)
VALUES (0, 'outpatient_fasttrack', '1.0.0', '{"actionBar":[{"actionCode":"submit","sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"DRAFT"}},{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":4,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"pay","confirm":{"content":"支付经资金两段式（硬预占→已支付），幂等键=报案号","contentKey":"confirm.pay.content","title":"确认支付","titleKey":"confirm.pay.title"},"sortOrder":5,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"APPROVED"}},{"actionCode":"appeal","sortOrder":6,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REJECTED"}}],"base":{"baseVersion":"1.0.0","followMode":"notify","templateCode":"outpatient_fasttrack"},"displayModes":["create","edit","view"],"entityCode":"claim_case","footer":{"aiBadge":true,"disclaimer":"拒赔结论附条款原文与证据位置；申诉 48h 响应","disclaimerKey":"footer.disclaimer"},"i18n":{"namespace":"bone.ui.claim_case"},"linkageRules":[{"code":"LINK-DUAL-01","name":"大额强制双人复核提示","then":[{"fieldCode":"second_reviewer","notice":"金额≥1 万元须双人复核（R-MAN-01）","noticeKey":"notice.dual_review","required":true,"visible":true}],"when":{"fieldCode":"claim_amount","operator":"gte","value":10000}},{"code":"LINK-EVID-01","name":"材料缺失转补件提示","then":[{"fieldCode":"evidences","notice":"材料缺失请补件，不阻塞其他案件（R-DEC P5）","required":true}],"when":{"fieldCode":"evidences","operator":"isNull"}},{"code":"LINK-FAST-01","name":"快赔资格四条件全满足才亮快赔入口（R-FAST-01，all 语义禁拆分）","then":[{"fieldCode":"fasttrack_entry","visible":true}],"when":{"all":[{"fieldCode":"calc_amount","operator":"lte","value":500},{"fieldCode":"evidences","operator":"isNotNull"},{"fieldCode":"fraud_flags","operator":"isNull"},{"fieldCode":"coverage_clear","operator":"eq","value":true}]}}],"schemaVersion":1,"submitRules":[{"code":"SUB-CONS-01","compare":{"fieldCode":"claim_amount","valueType":"field"},"fields":["claim_amount","items.amount"],"function":"consistency","message":"发票金额与清单合计不一致（差额>1 元，R-FRAUD-04）","messageKey":"submit.consistency","name":"发票与清单一致","operator":"eq","params":{"tolerance":1},"verifyType":"ERROR"},{"code":"SUB-COMP-01","fields":["policy_no","insured_name","claim_type","hospital_level","invoice_no","claim_amount"],"function":"completeness","message":"申请要素须完整（保单/被保人/类型/医院等级/发票号/金额 6 件套，R-MAN-03）","messageKey":"submit.reject_reasons","name":"申请要素完整性","operator":"gte","params":{"minReasons":6},"verifyType":"ERROR"}],"variants":[{"actionBar":[{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}}],"key":"reviewer","label":"核赔工作台","match":{"roles":["reviewer","admin"]},"priority":10,"views":{"detail":{"sections":[{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"}]},"form":{"sections":[{"controls":[{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}}],"id":"sec_calc","kind":"dashboard","title":"理算与风险（先看结论）"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]},"readonly":true}],"id":"sec_items","title":"费用明细（只读核对）"}]}}}],"views":{"form":{"sections":[{"controls":[{"controlKind":"INPUT","fieldCode":"policy_no"},{"controlKind":"INPUT","fieldCode":"insured_id_hash","props":{"mask":"hash"},"readonly":true},{"controlKind":"ENUM","fieldCode":"claim_type"},{"controlKind":"ENUM","fieldCode":"hospital_level"},{"controlKind":"INPUT","fieldCode":"invoice_no"},{"controlKind":"DATE","fieldCode":"invoice_date"}],"id":"sec_apply","title":"申请信息（员工端）","titleKey":"sections.sec_apply"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]}}],"id":"sec_items","title":"费用明细","titleKey":"sections.sec_items"},{"controls":[{"controlKind":"ATTACHMENT","fieldCode":"evidences","props":{"maxCount":20,"maxSizeMB":10}}],"id":"sec_evidences","title":"材料证据","titleKey":"sections.sec_evidences"},{"controls":[{"controlKind":"STAT","fieldCode":"deductible_used","props":{"lineage":"deductible-annual-cumu"},"virtual":true},{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}},{"controlKind":"NUMBER","fieldCode":"calc_amount","readonly":true,"virtual":true},{"controlKind":"SWITCH","fieldCode":"coverage_clear","readonly":true},{"controlKind":"SWITCH","fieldCode":"fasttrack_entry","readonly":true,"virtual":true}],"id":"sec_calc","kind":"dashboard","title":"理算引用（只读：规则引擎计算，后端为准）","titleKey":"sections.sec_calc"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹","titleKey":"sections.sec_trace"}]},"list":{"columns":[{"fieldCode":"claim_no","visible":true,"width":150},{"fieldCode":"insured_name","visible":true,"width":100},{"fieldCode":"hospital_level","visible":true,"width":110},{"fieldCode":"claim_amount","visible":true,"width":120},{"fieldCode":"payable_amount","visible":true,"width":120},{"fieldCode":"status","visible":true,"width":120}],"dataScope":{"policyCode":"reviewer_or_owner"},"searchFields":["status","hospital_level","claim_amount"],"stats":[{"title":"今日待办","titleKey":"stats.todo_today","valueRef":"todo_today"},{"title":"快赔自动通过","valueRef":"fasttrack_rate"},{"title":"转人工率","valueRef":"manual_rate"}],"tabs":[{"fieldCode":"reviewer_id","title":"待我审核","titleKey":"tabs.todo","value":"$me"},{"fieldCode":"fraud_flag","title":"风险标记","value":true},{"fieldCode":"cap_reached","title":"已达封顶","value":true}]}}}')
ON DUPLICATE KEY UPDATE schema_json = VALUES(schema_json);

-- 2.5 理赔界面 schema（草稿；base 指针 outpatient_fasttrack:1.0.0 + notify，模板现行 1.1.0 → base-update 有更新）。
INSERT INTO meta_entity_ui_schema
  (tenant_id, entity_id, schema_json, schema_version, schema_minor,
   published_schema_json, published_version, base_template_code, base_version, follow_mode,
   lock_version, created_at, updated_at, deleted)
VALUES (0, 9240, '{"actionBar":[{"actionCode":"submit","sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"DRAFT"}},{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":4,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"pay","confirm":{"content":"支付经资金两段式（硬预占→已支付），幂等键=报案号","contentKey":"confirm.pay.content","title":"确认支付","titleKey":"confirm.pay.title"},"sortOrder":5,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"APPROVED"}},{"actionCode":"appeal","sortOrder":6,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REJECTED"}}],"base":{"baseVersion":"1.0.0","followMode":"notify","templateCode":"outpatient_fasttrack"},"displayModes":["create","edit","view"],"entityCode":"claim_case","footer":{"aiBadge":true,"disclaimer":"拒赔结论附条款原文与证据位置；申诉 48h 响应","disclaimerKey":"footer.disclaimer"},"i18n":{"namespace":"bone.ui.claim_case"},"linkageRules":[{"code":"LINK-DUAL-01","name":"大额强制双人复核提示","then":[{"fieldCode":"second_reviewer","notice":"金额≥1 万元须双人复核（R-MAN-01）","noticeKey":"notice.dual_review","required":true,"visible":true}],"when":{"fieldCode":"claim_amount","operator":"gte","value":10000}},{"code":"LINK-EVID-01","name":"材料缺失转补件提示","then":[{"fieldCode":"evidences","notice":"材料缺失请补件，不阻塞其他案件（R-DEC P5）","required":true}],"when":{"fieldCode":"evidences","operator":"isNull"}},{"code":"LINK-FAST-01","name":"快赔资格四条件全满足才亮快赔入口（R-FAST-01，all 语义禁拆分）","then":[{"fieldCode":"fasttrack_entry","visible":true}],"when":{"all":[{"fieldCode":"calc_amount","operator":"lte","value":500},{"fieldCode":"evidences","operator":"isNotNull"},{"fieldCode":"fraud_flags","operator":"isNull"},{"fieldCode":"coverage_clear","operator":"eq","value":true}]}}],"schemaVersion":1,"submitRules":[{"code":"SUB-CONS-01","compare":{"fieldCode":"claim_amount","valueType":"field"},"fields":["claim_amount","items.amount"],"function":"consistency","message":"发票金额与清单合计不一致（差额>1 元，R-FRAUD-04）","messageKey":"submit.consistency","name":"发票与清单一致","operator":"eq","params":{"tolerance":1},"verifyType":"ERROR"},{"code":"SUB-COMP-01","fields":["policy_no","insured_name","claim_type","hospital_level","invoice_no","claim_amount"],"function":"completeness","message":"申请要素须完整（保单/被保人/类型/医院等级/发票号/金额 6 件套，R-MAN-03）","messageKey":"submit.reject_reasons","name":"申请要素完整性","operator":"gte","params":{"minReasons":6},"verifyType":"ERROR"}],"variants":[{"actionBar":[{"actionCode":"approve","decisionKind":"hitl","requireDualApproveWhen":{"fieldCode":"claim_amount","operator":"gte","value":10000},"sortOrder":1,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"reject","decisionKind":"hitl","reasonTypes":["责任范围不符","目录外不赔","已达封顶","风险标记","材料造假","其他"],"sortOrder":2,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}},{"actionCode":"requestMoreEvidence","sortOrder":3,"visibleWhen":{"fieldCode":"status","operator":"eq","value":"REVIEWING"}}],"key":"reviewer","label":"核赔工作台","match":{"roles":["reviewer","admin"]},"priority":10,"views":{"detail":{"sections":[{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"}]},"form":{"sections":[{"controls":[{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}}],"id":"sec_calc","kind":"dashboard","title":"理算与风险（先看结论）"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]},"readonly":true}],"id":"sec_items","title":"费用明细（只读核对）"}]}}}],"views":{"form":{"sections":[{"controls":[{"controlKind":"INPUT","fieldCode":"policy_no"},{"controlKind":"INPUT","fieldCode":"insured_id_hash","props":{"mask":"hash"},"readonly":true},{"controlKind":"ENUM","fieldCode":"claim_type"},{"controlKind":"ENUM","fieldCode":"hospital_level"},{"controlKind":"INPUT","fieldCode":"invoice_no"},{"controlKind":"DATE","fieldCode":"invoice_date"}],"id":"sec_apply","title":"申请信息（员工端）","titleKey":"sections.sec_apply"},{"controls":[{"controlKind":"CHILD_TABLE","fieldCode":"items","props":{"childEntity":"claim_case_item","columns":["name","amount","category"]}}],"id":"sec_items","title":"费用明细","titleKey":"sections.sec_items"},{"controls":[{"controlKind":"ATTACHMENT","fieldCode":"evidences","props":{"maxCount":20,"maxSizeMB":10}}],"id":"sec_evidences","title":"材料证据","titleKey":"sections.sec_evidences"},{"controls":[{"controlKind":"STAT","fieldCode":"deductible_used","props":{"lineage":"deductible-annual-cumu"},"virtual":true},{"controlKind":"COMPUTED","fieldCode":"payable_amount","props":{"computedFn":"payableAmount","computedFrom":["claim_amount","deductible_used"],"lineage":"rat-cap-formula"}},{"controlKind":"ALERT","fieldCode":"fraud_flags","props":{"warningText":"命中 R-FRAUD 标记一律转人工（P0 优先级）"}},{"controlKind":"NUMBER","fieldCode":"calc_amount","readonly":true,"virtual":true},{"controlKind":"SWITCH","fieldCode":"coverage_clear","readonly":true},{"controlKind":"SWITCH","fieldCode":"fasttrack_entry","readonly":true,"virtual":true}],"id":"sec_calc","kind":"dashboard","title":"理算引用（只读：规则引擎计算，后端为准）","titleKey":"sections.sec_calc"},{"controls":[{"controlKind":"TIMELINE","fieldCode":"decision_trace","virtual":true}],"id":"sec_trace","kind":"timeline","title":"决策轨迹","titleKey":"sections.sec_trace"}]},"list":{"columns":[{"fieldCode":"claim_no","visible":true,"width":150},{"fieldCode":"insured_name","visible":true,"width":100},{"fieldCode":"hospital_level","visible":true,"width":110},{"fieldCode":"claim_amount","visible":true,"width":120},{"fieldCode":"payable_amount","visible":true,"width":120},{"fieldCode":"status","visible":true,"width":120}],"dataScope":{"policyCode":"reviewer_or_owner"},"searchFields":["status","hospital_level","claim_amount"],"stats":[{"title":"今日待办","titleKey":"stats.todo_today","valueRef":"todo_today"},{"title":"快赔自动通过","valueRef":"fasttrack_rate"},{"title":"转人工率","valueRef":"manual_rate"}],"tabs":[{"fieldCode":"reviewer_id","title":"待我审核","titleKey":"tabs.todo","value":"$me"},{"fieldCode":"fraud_flag","title":"风险标记","value":true},{"fieldCode":"cap_reached","title":"已达封顶","value":true}]}}}', 1, 1,
   NULL, NULL, 'outpatient_fasttrack', '1.0.0', 'notify',
   0, NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE schema_json = VALUES(schema_json), schema_minor = 1,
    base_template_code = VALUES(base_template_code), base_version = VALUES(base_version),
    follow_mode = VALUES(follow_mode), updated_at = NOW(3);

-- ── 3. 校验 ──────────────────────────────────────────────────────────────
SELECT
  (SELECT COUNT(*) FROM meta_entity_ui_schema WHERE deleted = 0
     AND entity_id = @exp_entity_id AND tenant_id = @exp_tenant_id) AS expense_schema_ready,
  (SELECT COUNT(*) FROM meta_entity_ui_schema WHERE tenant_id = 0 AND entity_id = 9240 AND deleted = 0) AS claim_schema_ready,
  (SELECT COUNT(*) FROM meta_field WHERE tenant_id = 0 AND entity_id = 9240 AND deleted = 0) AS claim_field_count,
  (SELECT COUNT(*) FROM meta_entity_action_def WHERE tenant_id = 0 AND entity_code = 'claim_case' AND deleted = 0) AS claim_action_count,
  (SELECT ui_schema_version FROM meta_model_template WHERE tenant_id = 0 AND code = 'outpatient_fasttrack' AND deleted = 0) AS tpl_current,
  (SELECT COUNT(*) FROM meta_model_template_ui_history WHERE tenant_id = 0 AND template_code = 'outpatient_fasttrack' AND version = '1.0.0') AS tpl_history_ready;

-- 回滚（手工执行，顺序注意外键逻辑）：
-- DELETE FROM meta_entity_ui_schema WHERE tenant_id = 0 AND entity_id = 9240;
-- DELETE FROM meta_entity_ui_schema WHERE entity_id IN (SELECT id FROM meta_entity WHERE code = 'expense_claim');
-- DELETE FROM meta_field WHERE tenant_id = 0 AND entity_id IN (9240, 9245);
-- DELETE FROM meta_entity WHERE tenant_id = 0 AND id IN (9240, 9245);
-- DELETE FROM meta_entity_action_def WHERE tenant_id = 0 AND entity_code = 'claim_case';
-- DELETE FROM meta_entity_action_def WHERE tenant_id <> 0 AND entity_code = 'expense_claim';
-- DELETE FROM meta_model_template WHERE tenant_id = 0 AND code = 'outpatient_fasttrack';
-- DELETE FROM meta_model_template_ui_history WHERE tenant_id = 0 AND template_code = 'outpatient_fasttrack';
-- DROP TABLE IF EXISTS t_claim_case_item; DROP TABLE IF EXISTS t_claim_case;
