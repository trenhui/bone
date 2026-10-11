-- =============================================================================
-- 0031_scenario_template_expense.sql
--
-- 目的（PRD v0.39 · W1a T4）：把原型/PRD §2.5.9 / cases 文档里的「费用报销」场景
--       落成平台模板数据（meta_model_template + meta_model_template_field），
--       使「巡检 / 补货 / 访客 / 费用报销」四套场景在生产可复现，UC-04「换场景只换数据」
--       对报销同样成立；并以 2 个 ENUM（category / status）承载报销轻状态机（C2 闭环前提）。
--
-- 关联：0029_scenario_templates.sql（本脚本是其同构扩展，巡检/补货/访客已落）；
--       0028_meta_template_enum.sql（enum_values / validation_rules 列依赖，须先执行）。
--
-- 设计
-- ----------------------
-- · 平台层共享（tenant_id = 0），status = 1（已发布，可实例化）。
-- · L0 黄金路径单实体：9 字段，含 category(5 选) + status(5 态) 两个 ENUM。
--   DECIMAL 金额 + 双 ENUM 验 Mode B 自动生成界面（对齐 PRD §2.5.9）。
-- · 幂等：模板按 code（uk_meta_tpl_code）、字段按 (template_id, code)（uk_meta_tpl_f_code）
--   做 ON DUPLICATE KEY UPDATE，可重复执行。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行，本脚本不在会话内自动执行 INSERT 以外的写）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0031_scenario_template_expense.sql
-- 前置：0028_meta_template_enum.sql、0029_scenario_templates.sql 已执行。
-- =============================================================================

USE bone;

-- ── 0. 依赖护栏：0028 未执行则直接报错（列不存在 → MySQL 1054） ──────────
SELECT enum_values FROM meta_model_template_field LIMIT 0;

-- ── 1. 模板主表（费用报销） ─────────────────────────────────────────────
INSERT INTO meta_model_template
    (id, tenant_id, code, name, description, category, current_version, status, created_by, created_at, updated_at, deleted, version)
VALUES
    (9150, 0, 'tpl_expense_claim', '费用报销', '办公域费用报销场景模板：报销单/金额(DECIMAL)/类目(ENUM)/5 态状态机(ENUM)，开箱即用，实例化后按需扩展字段（如 cost_center）', '行政财务', 'v1.0.0', 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description),
    current_version = VALUES(current_version), status = VALUES(status);

-- ── 2. 模板字段集（9 字段，含 2 个 ENUM） ─────────────────────────────
-- 列序：(id, tenant_id, template_id, code, name, display_name, field_type,
--        length, is_required, is_unique, default_value, comment, sort_order,
--        enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    -- 费用报销（9150）：9 字段，含 category ENUM + status 5 态状态机 ENUM
    (9151, 0, 9150, 'claim_no',     'claimNo',     '报销单号', 'string',  64,  1, 1, NULL,     '业务唯一编码', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9152, 0, 9150, 'title',        'title',       '事由摘要', 'string',  128, 1, 0, NULL,     '报销事由', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9153, 0, 9150, 'amount',       'amount',      '金额',     'decimal', NULL,1, 0, NULL,     '报销金额（元）', 3, NULL, '{"min":0.01}', 1, NOW(3), NOW(3), 0, 0),
    (9154, 0, 9150, 'category',     'category',    '费用类目', 'ENUM',    NULL,1, 0, NULL,     '差旅/餐饮/交通/办公/其他', 4,
        '[{"code":"TRAVEL","label":"差旅"},{"code":"MEAL","label":"餐饮"},{"code":"TRANSPORT","label":"交通"},{"code":"OFFICE","label":"办公"},{"code":"OTHER","label":"其他"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9155, 0, 9150, 'expense_date', 'expenseDate', '发生日期', 'date',    NULL,1, 0, NULL,     '费用发生日期', 5, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9156, 0, 9150, 'status',       'status',      '状态',     'ENUM',    NULL,1, 0, 'DRAFT',  '5 态轻状态机', 6,
        '[{"code":"DRAFT","label":"草稿"},{"code":"SUBMITTED","label":"已提交"},{"code":"APPROVED","label":"已审批"},{"code":"REJECTED","label":"已驳回"},{"code":"PAID","label":"已打款"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9157, 0, 9150, 'applicant',    'applicant',   '申请人',   'string',  64,  1, 0, NULL,     '报销人姓名（MVP 文本；正式可 REFERENCE→User）', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9158, 0, 9150, 'remark',       'remark',      '说明',     'text',    NULL,0, 0, NULL,     '补充说明', 8, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9159, 0, 9150, 'cost_center',  'costCenter',  '成本中心', 'string',  64,  0, 0, NULL,     '热加载演示字段（1→n 再加；部门/项目归集）', 9, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), is_required = VALUES(is_required),
    is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules);

-- ── 3. 校验 ─────────────────────────────────────────────────────────────
SELECT
    (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND code = 'tpl_expense_claim') AS template_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND t.code = 'tpl_expense_claim') AS field_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND t.code = 'tpl_expense_claim' AND f.field_type = 'ENUM') AS enum_field_count;
