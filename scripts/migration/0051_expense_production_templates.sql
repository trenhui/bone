-- =============================================================================
-- 0051_expense_production_templates.sql
--
-- 目的（0-1 建设方案 Step 2 · 决策 D7）：把「企业报销应用」的 §4 模型规格落成
--       **平台生产模板**（tenant_id = 0），使任何租户可一键实例化出完整报销域：
--       报销单（头）+ 明细行 + 类别主数据 + 借款单，含全量字段、关系类型字段
--       （REFERENCE / CHILD_TABLE / ATTACHMENT）、状态机动作（审票/撤回/重开/取消/
--       打款）与基础界面资产（ui_schema_json）。
--
-- 与既有资产的关系（不重造）：
--   · 0031 tpl_expense_reimbursement（id 9150）= L0 探针模板，**不演迁不复制**（D7）；
--     本迁移建 _v2 生产模板（code 后缀 _v2，id 9170/9190/9400/9410，避开 0047 的 9160）。
--   · 字段/动作语义与 0037（实例级 L1）、0032（动作 SPI）、0040（模板动作）、
--     0048（is_entry）、0046（模板 UI）逐列对齐；实例级关系登记（meta_entity_relation）、
--     行策略（meta_entity.row_policies，0038）、auto_number（0043）不在模板层，
--     实例化后按租户接线（建模工作台或租户迁移，参照 0037/0038 模式）。
--
-- 设计要点：
--   · 一模板一实体（平台既有机制，0031/0029 实证）；四模板组合成报销域。
--   · delivery_mode = 1（RUNTIME，0041）：实例化发布即建物理表；实例 table_name
--     按数据库开发规范 §3 模块前缀命名（D6：expense_reimbursement/expense_reimbursement_item/exp_category/
--     exp_advance；实体 code ≠ 物理表名，禁 t_ 前缀——规范 §2.11）。
--   · field_type 统一大写（对齐 meta_field / FIELD_TYPE_MAP 口径；0031 的小写标量
--     系历史写法）。REFERENCE/CHILD_TABLE/ATTACHMENT 经 validation_rules 内嵌 JSON
--     声明（0037 同构），实例化器逐列透传（MetaTemplateApplicationService:94-124）。
--   · 逃逸动作 confirmPayout：claim 用既有 expenseReimbursementConfirmPayoutHandler；
--     advance 用 expenseAdvanceConfirmPayoutHandler（L1 同批新增，未落地前该动作
--      dispatch 会失败——模板先行、Bean 后到，属已知排期，见 0-1 方案 §10）。
--   · confirmPayout 的 from_status = AUDITED（审票后打款，SoD）；无审票企业实例化后
--     数据级裁剪（改 from_status = APPROVED + 删 audit 动作），见方案 D10。
--   · currency 在模板层为 inline ENUM（模板字段表无 enum_source 列，0050 仅实例层）；
--     v1.1 实例化后迁 enum_source = reference:CURRENCY（doc 11）。
--   · ui_schema_json 为**基础资产**（实例化即可用界面）；行级发票必填等精细规则
--     实例化后在设计器按租户完善（方案 §5）。
--
-- 执行（L3 审批项；由 DBA / CI 在目标库运行，本脚本不自动执行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0051_expense_production_templates.sql
-- 前置：0028（模板 enum/validation 列）、0040（meta_model_template_action）、
--       0041（delivery_mode）、0046（ui_schema_json 列）、0048（is_entry）已执行。
-- 幂等：模板按 uk_meta_tpl_code、字段按 uk_meta_tpl_f_code、动作按 uk_tpl_action_code
--       做 ON DUPLICATE KEY UPDATE，可重复执行。
-- =============================================================================

USE bone;

-- ── 0. 依赖护栏：前置迁移未执行则直接报错 ──────────────────────────────
SELECT enum_values FROM meta_model_template_field LIMIT 0;          -- 0028
SELECT delivery_mode FROM meta_model_template LIMIT 0;             -- 0041
SELECT is_escape FROM meta_model_template_action LIMIT 0;          -- 0040
SELECT is_entry FROM meta_model_template_action LIMIT 0;           -- 0048
SELECT ui_schema_json FROM meta_model_template LIMIT 0;            -- 0046

-- ── 1. 模板主表（四实体，平台层共享，RUNTIME 交付） ─────────────────────
INSERT INTO meta_model_template
    (id, tenant_id, code, name, description, category, current_version, status, delivery_mode,
     ui_schema_version, ui_schema_json, created_by, created_at, updated_at, deleted, version)
VALUES
    (9170, 0, 'tpl_expense_reimbursement_v2', '费用报销单（生产）',
     '企业报销域·报销单：头行金额+多明细行+类别引用+发票影像+审批人；7 态状态机（提交/审批/审票/打款/驳回/撤回/重开/取消）；实例化 code=expense_reimbursement、table=expense_reimbursement（模块前缀 exp_，数据库开发规范 §3）',
     '行政财务', 'v2.0.0', 1, 1,
     NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9190, 0, 'tpl_expense_reimbursement_item_v2', '报销明细行（生产）',
     '企业报销域·明细子实体：CHILD_TABLE 行（claim_id 外键）、行级类别/金额/发票类型/发票号/税号/税率/行级附件；无状态机（随主单流转）',
     '行政财务', 'v2.0.0', 1, 1,
     NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9400, 0, 'tpl_expense_category_v2', '报销类别（生产）',
     '企业报销域·类别主数据：编码/名称/启用/排序（REFERENCE 目标实体，type=1 主数据）；实例化后灌初始 5 类（差旅/餐饮/交通/办公/其他，参照 0037 §2）',
     '行政财务', 'v2.0.0', 1, 1,
     NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9410, 0, 'tpl_expense_advance_v2', '借款单（生产）',
     '企业报销域·备用金借款单：金额/事由/预计归还；6 态状态机（提交/审批/打款/驳回/撤回/重开/取消）；与报销的核销 v1.1（方案 §9 G12）',
     '行政财务', 'v2.0.0', 1, 1,
     NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description),
    current_version = VALUES(current_version), status = VALUES(status),
    delivery_mode = VALUES(delivery_mode),
    ui_schema_version = VALUES(ui_schema_version), ui_schema_json = VALUES(ui_schema_json),
    updated_at = NOW(3);

-- ── 2. 模板字段集 ─────────────────────────────────────────────────────
-- 列序：(id, tenant_id, template_id, code, name, display_name, field_type,
--        length, is_required, is_unique, default_value, comment, sort_order,
--        enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)

-- 2.1 报销单 tpl_expense_reimbursement_v2（9170）：16 字段（方案 §4.2）
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    (9171, 0, 9170, 'claim_no',       'claimNo',       '报销单号',   'STRING',  32,  1, 1, NULL,     '业务唯一编码（实例层接 auto_number，0043）', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9172, 0, 9170, 'title',          'title',         '报销事由',   'STRING',  200, 1, 0, NULL,     '报销事由/标题', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9173, 0, 9170, 'applicant',      'applicant',     '申请人',     'STRING',  64,  0, 0, NULL,     '展示字段：created_by 自动填充（行策略用 created_by，与本字段解耦）', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9174, 0, 9170, 'department',     'department',    '部门',       'STRING',  64,  0, 0, NULL,     '辅助核算维度（部门）；mdm ORG_UNIT 结构化引用 v1.1', 4, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9175, 0, 9170, 'expense_start',  'expenseStart',  '行程开始',   'DATE',    NULL,0, 0, NULL,     '差旅行程；v1.1 差旅津贴计算基线', 5, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9176, 0, 9170, 'expense_end',    'expenseEnd',    '行程结束',   'DATE',    NULL,0, 0, NULL,     'linkage：不得早于行程开始', 6, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9177, 0, 9170, 'expense_date',   'expenseDate',   '报销日期',   'DATE',    NULL,1, 0, NULL,     '费用发生日期', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9178, 0, 9170, 'category_id',    'categoryId',    '费用类别',   'REFERENCE', NULL, 0, 0, NULL,   'REFERENCE → expense_category（labelField=name）', 8, NULL,
        '{"reference":{"targetEntityCode":"expense_category","labelField":"name"}}', 1, NOW(3), NOW(3), 0, 0),
    (9179, 0, 9170, 'amount',         'amount',        '申报金额',   'DECIMAL', NULL,1, 0, NULL,     '=Σ明细：ReimbursementChildAmountAggregator 服务端回填 + 提交对账；SUBMITTED 后冻结', 9, NULL, '{"min":0.01}', 1, NOW(3), NOW(3), 0, 0),
    (9180, 0, 9170, 'currency',       'currency',      '币种',       'ENUM',    NULL,1, 0, 'CNY',    'v1.1 迁 enum_source=reference:CURRENCY（模板层无该列）', 10,
        '[{"code":"CNY","label":"人民币"},{"code":"USD","label":"美元"},{"code":"HKD","label":"港币"},{"code":"EUR","label":"欧元"},{"code":"JPY","label":"日元"},{"code":"GBP","label":"英镑"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9181, 0, 9170, 'approver_id',    'approverId',    '审批人',     'REFERENCE', NULL, 0, 0, NULL,   'REFERENCE → iam_account（IAM 账号表，审批人须可登录账号；SoD 禁=applicant）；依赖 IAM 账号的运行时暴露，未暴露前降级 STRING；值语义=iam_account.id（行策略 :operator 同源）', 11, NULL,
        '{"reference":{"targetEntityCode":"iam_account","labelField":"name"}}', 1, NOW(3), NOW(3), 0, 0),
    (9182, 0, 9170, 'status',         'status',        '状态',       'ENUM',    NULL,1, 0, 'DRAFT',  '7 态状态机（动作定义驱动）', 12,
        '[{"code":"DRAFT","label":"草稿"},{"code":"SUBMITTED","label":"已提交"},{"code":"APPROVED","label":"已审批"},{"code":"AUDITED","label":"已审票"},{"code":"PAID","label":"已打款"},{"code":"REJECTED","label":"已驳回"},{"code":"CANCELLED","label":"已取消"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9183, 0, 9170, 'attachment',     'attachment',    '发票影像',   'ATTACHMENT', NULL, 0, 0, NULL,  '头行附件：行程单/汇总单（JSON 清单，TEXT 物理列）；行级发票见明细实体', 13, NULL,
        '{"attachment":{"maxCount":10,"maxSizeMB":10,"accept":["image/*","application/pdf"]}}', 1, NOW(3), NOW(3), 0, 0),
    (9184, 0, 9170, 'items',          'items',         '费用明细',   'CHILD_TABLE', NULL, 0, 0, NULL,  '1—N 明细行声明字段（无物理列）', 14, NULL,
        '{"childTable":{"childEntityCode":"expense_reimbursement_item","foreignKey":"claim_id","orderColumn":"sort_order","cascadeDelete":true,"minRows":0,"maxRows":200}}', 1, NOW(3), NOW(3), 0, 0),
    (9185, 0, 9170, 'cost_center',    'costCenter',    '成本中心',   'STRING',  64,  0, 0, NULL,     '辅助核算维度（成本中心）；报账过账用', 15, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9186, 0, 9170, 'remark',         'remark',        '备注',       'TEXT',    NULL,0, 0, NULL,     '补充说明', 16, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), field_type = VALUES(field_type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), updated_at = NOW(3);

-- 2.2 明细行 tpl_expense_reimbursement_item_v2（9190）：11 字段（方案 §4.3）
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    (9191, 0, 9190, 'claim_id',      'claimId',      '所属报销单', 'LONG',    NULL,1, 0, NULL,     'CHILD_TABLE 外键 → 报销单 id', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9192, 0, 9190, 'sort_order',    'sortOrder',    '行序',       'INTEGER', NULL,0, 0, '0',      'CHILD_TABLE orderColumn', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9193, 0, 9190, 'expense_date',  'expenseDate',  '发生日期',   'DATE',    NULL,1, 0, NULL,     '本行费用发生日期', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9194, 0, 9190, 'category_id',   'categoryId',   '费用类别',   'REFERENCE', NULL, 0, 0, NULL,   'REFERENCE → expense_category', 4, NULL,
        '{"reference":{"targetEntityCode":"expense_category","labelField":"name"}}', 1, NOW(3), NOW(3), 0, 0),
    (9195, 0, 9190, 'amount',        'amount',       '行金额',     'DECIMAL', NULL,1, 0, NULL,     '服务端强制 min:0.01；Σ 回填头表', 5, NULL, '{"min":0.01}', 1, NOW(3), NOW(3), 0, 0),
    (9196, 0, 9190, 'invoice_type',  'invoiceType',  '发票类型',   'ENUM',    NULL,0, 0, NULL,     'v1.1 迁 enum_source=dict:expense_invoice_type', 6,
        '[{"code":"SPECIAL","label":"增值税专用发票"},{"code":"NORMAL","label":"增值税普通发票"},{"code":"ELECTRONIC","label":"电子发票"},{"code":"NONE","label":"无票"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9197, 0, 9190, 'invoice_no',    'invoiceNo',    '发票号',     'STRING',  64,  0, 0, NULL,     'linkage：专票必填；查重 v1.1（方案 §9 G9）', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9198, 0, 9190, 'tax_no',        'taxNo',        '税号',       'STRING',  64,  0, 0, NULL,     'linkage：专票必填', 8, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9199, 0, 9190, 'tax_rate',      'taxRate',      '税率',       'DECIMAL', NULL,0, 0, NULL,     '专票填写；价税分离', 9, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9200, 0, 9190, 'attachment',    'attachment',   '发票影像',   'ATTACHMENT', NULL, 0, 0, NULL,  '行级附件：每票对一行（收票即校验）', 10, NULL,
        '{"attachment":{"maxCount":5,"maxSizeMB":10,"accept":["image/*","application/pdf"]}}', 1, NOW(3), NOW(3), 0, 0),
    (9201, 0, 9190, 'remark',        'remark',       '备注',       'TEXT',    NULL,0, 0, NULL,     '本行备注', 11, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), field_type = VALUES(field_type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), updated_at = NOW(3);

-- 2.3 类别主数据 tpl_expense_category_v2（9400）：4 字段（方案 §4.1）
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    (9401, 0, 9400, 'code',       'code',       '类别编码', 'STRING',  64,  1, 1, NULL, '类别编码（唯一；科目映射按编码匹配，业财一体 D11）', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9402, 0, 9400, 'name',       'name',       '类别名称', 'STRING',  100, 1, 0, NULL, 'REFERENCE labelField', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9403, 0, 9400, 'enabled',    'enabled',    '是否启用', 'BOOLEAN', NULL,0, 0, '1',  '停用后不再出现在下拉（存量回显不受影响）', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9404, 0, 9400, 'sort_order', 'sortOrder',  '排序',     'INTEGER', NULL,0, 0, '0',  '下拉排序', 4, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), field_type = VALUES(field_type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order), updated_at = NOW(3);

-- 2.4 借款单 tpl_expense_advance_v2（9410）：7 字段（方案 §4.4）
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    (9411, 0, 9410, 'advance_no',           'advanceNo',          '借款单号',   'STRING',  32,  1, 1, NULL, '业务唯一编码（实例层接 auto_number）', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9412, 0, 9410, 'applicant',            'applicant',          '借款人',     'STRING',  64,  0, 0, NULL, 'created_by 自动填充', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9413, 0, 9410, 'amount',               'amount',             '借款金额',   'DECIMAL', NULL,1, 0, NULL, '单笔借款（无明细结构）', 3, NULL, '{"min":0.01}', 1, NOW(3), NOW(3), 0, 0),
    (9414, 0, 9410, 'reason',               'reason',             '借款事由',   'TEXT',    NULL,1, 0, NULL, '借款原因', 4, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9415, 0, 9410, 'expected_return_date', 'expectedReturnDate', '预计归还',   'DATE',    NULL,0, 0, NULL, '账期管理；超期提醒（方案 §7.3）', 5, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9416, 0, 9410, 'status',               'status',             '状态',       'ENUM',    NULL,1, 0, 'DRAFT', '6 态状态机', 6,
        '[{"code":"DRAFT","label":"草稿"},{"code":"SUBMITTED","label":"已提交"},{"code":"APPROVED","label":"已审批"},{"code":"PAID","label":"已放款"},{"code":"REJECTED","label":"已驳回"},{"code":"CANCELLED","label":"已取消"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9417, 0, 9410, 'remark',               'remark',             '备注',       'TEXT',    NULL,0, 0, NULL, '补充说明', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), field_type = VALUES(field_type), length = VALUES(length),
    is_required = VALUES(is_required), is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules), updated_at = NOW(3);

-- ── 3. 状态机动作（实例化展开为 meta_entity_action_def；is_entry 显式声明） ──
-- 列序：(tenant_id, template_id, action_code, label, from_status, to_status, status_field,
--        is_escape, handler_bean, params_schema, requires_idempotency, sort_order, is_entry,
--        created_at, updated_at, deleted, version)

-- 3.1 报销单（9170）：10 动作——提交/审批/驳回/审票/审退/撤回/重开/取消×2/打款
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order, is_entry,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9170, 'submit',        '提交',     'DRAFT',     'SUBMITTED', 'status', 0, NULL, NULL, 0, 10, 1, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'approve',       '审批通过', 'SUBMITTED', 'APPROVED',  'status', 0, NULL,
      '[{"name":"comment","type":"string","required":false,"label":"审批意见"}]', 0, 20, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'reject',        '驳回',     'SUBMITTED', 'REJECTED',  'status', 0, NULL,
      '[{"name":"reason","type":"string","required":true,"label":"驳回原因"}]', 0, 21, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'audit',         '财务审票', 'APPROVED',  'AUDITED',   'status', 0, NULL,
      '[{"name":"comment","type":"string","required":false,"label":"审票意见"}]', 0, 30, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'auditReject',   '审票退回', 'APPROVED',  'REJECTED',  'status', 0, NULL,
      '[{"name":"reason","type":"string","required":true,"label":"退回原因"}]', 0, 31, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'withdraw',      '撤回',     'SUBMITTED', 'DRAFT',     'status', 0, NULL, NULL, 0, 40, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'reopen',        '重新打开', 'REJECTED',  'DRAFT',     'status', 0, NULL, NULL, 0, 41, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'cancel',        '取消',     'DRAFT',     'CANCELLED', 'status', 0, NULL, NULL, 0, 50, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'cancelRejected','取消（驳后）','REJECTED','CANCELLED', 'status', 0, NULL, NULL, 0, 51, 0, NOW(3), NOW(3), 0, 0),
  (0, 9170, 'confirmPayout', '确认打款', 'AUDITED',   'PAID',      'status', 1, 'expenseReimbursementConfirmPayoutHandler',
      '[{"name":"channel","type":"enum","required":true,"values":["PAYROLL","BANK_TRANSFER"],"label":"打款渠道"},{"name":"note","type":"string","required":false,"label":"备注"}]',
      1, 60, 0, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), is_entry = VALUES(is_entry), updated_at = NOW(3), version = version + 1;

-- 3.2 借款单（9410）：8 动作——提交/审批/驳回/撤回/重开/取消×2/放款
INSERT INTO meta_model_template_action
  (tenant_id, template_id, action_code, label, from_status, to_status, status_field,
   is_escape, handler_bean, params_schema, requires_idempotency, sort_order, is_entry,
   created_at, updated_at, deleted, version)
VALUES
  (0, 9410, 'submit',        '提交',     'DRAFT',     'SUBMITTED', 'status', 0, NULL, NULL, 0, 10, 1, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'approve',       '审批通过', 'SUBMITTED', 'APPROVED',  'status', 0, NULL,
      '[{"name":"comment","type":"string","required":false,"label":"审批意见"}]', 0, 20, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'reject',        '驳回',     'SUBMITTED', 'REJECTED',  'status', 0, NULL,
      '[{"name":"reason","type":"string","required":true,"label":"驳回原因"}]', 0, 21, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'withdraw',      '撤回',     'SUBMITTED', 'DRAFT',     'status', 0, NULL, NULL, 0, 40, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'reopen',        '重新打开', 'REJECTED',  'DRAFT',     'status', 0, NULL, NULL, 0, 41, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'cancel',        '取消',     'DRAFT',     'CANCELLED', 'status', 0, NULL, NULL, 0, 50, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'cancelRejected','取消（驳后）','REJECTED','CANCELLED', 'status', 0, NULL, NULL, 0, 51, 0, NOW(3), NOW(3), 0, 0),
  (0, 9410, 'confirmPayout', '确认放款', 'APPROVED',  'PAID',      'status', 1, 'expenseAdvanceConfirmPayoutHandler',
      '[{"name":"channel","type":"enum","required":true,"values":["PAYROLL","BANK_TRANSFER"],"label":"放款渠道"},{"name":"note","type":"string","required":false,"label":"备注"}]',
      1, 60, 0, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
  label = VALUES(label), from_status = VALUES(from_status), to_status = VALUES(to_status),
  status_field = VALUES(status_field), is_escape = VALUES(is_escape), handler_bean = VALUES(handler_bean),
  params_schema = VALUES(params_schema), requires_idempotency = VALUES(requires_idempotency),
  sort_order = VALUES(sort_order), is_entry = VALUES(is_entry), updated_at = NOW(3), version = version + 1;

-- 明细行（9190）/ 类别（9400）无状态机（随主单流转 / 纯主数据），不产动作定义（0040 同例）。

-- ── 4. 报销单基础界面资产（实例化即复制为租户 uiSchema 草稿；0046 机制） ────
-- 口径：entityCode 用实例实体 code（expense_reimbursement；样例 JSON 原 t_ 前缀系表名笔误，已随 §4.1 修正）；
--       actionBar 对齐 v3 状态机（10 动作）；linkage ①-⑤ 中附件必填仅前端强制
--       （UiSchemaRuleEnforcer 强制域不含 ATTACHMENT，方案 D11）。
UPDATE meta_model_template
SET ui_schema_version = '1.0.0',
    ui_schema_json = '{
  "schemaVersion": 1,
  "entityCode": "expense_reimbursement",
  "displayModes": ["create", "edit", "view"],
  "views": {
    "list": {
      "columns": [
        {"fieldCode": "claim_no", "visible": true, "width": 140},
        {"fieldCode": "title", "visible": true, "width": 220},
        {"fieldCode": "amount", "visible": true, "width": 130, "format": {"currencyField": "currency", "precision": 2}},
        {"fieldCode": "category_id", "visible": true, "width": 120},
        {"fieldCode": "status", "visible": true, "width": 100},
        {"fieldCode": "applicant", "visible": true, "width": 100},
        {"fieldCode": "expense_date", "visible": true, "width": 120}
      ],
      "searchFields": ["department", "status", "claim_no"],
      "tabs": [
        {"title": "待我审批", "fieldCode": "approver_id", "value": "$me"},
        {"title": "我提交的", "fieldCode": "applicant", "value": "$me"},
        {"title": "全部", "fieldCode": "status", "value": "$all"}
      ],
      "dataScope": {"policyCode": "owner_or_approver"},
      "batchActions": ["submit"]
    },
    "form": {
      "sections": [
        {"id": "sec_base", "title": "基本信息", "controls": [
          {"fieldCode": "title", "controlKind": "INPUT", "placeholder": "如：10 月差旅报销"},
          {"fieldCode": "category_id", "controlKind": "REFERENCE", "props": {"targetEntity": "expense_category", "labelField": "name", "valueField": "id"}},
          {"fieldCode": "applicant", "controlKind": "INPUT", "readonly": true},
          {"fieldCode": "department", "controlKind": "INPUT"},
          {"fieldCode": "expense_date", "controlKind": "DATE"}
        ]},
        {"id": "sec_travel", "title": "行程与金额", "controls": [
          {"fieldCode": "expense_start", "controlKind": "DATE"},
          {"fieldCode": "expense_end", "controlKind": "DATE"},
          {"fieldCode": "amount", "controlKind": "NUMBER", "props": {"min": 0.01, "precision": 2}},
          {"fieldCode": "currency", "controlKind": "ENUM"},
          {"fieldCode": "cost_center", "controlKind": "INPUT"}
        ]},
        {"id": "sec_items", "title": "费用明细", "controls": [
          {"fieldCode": "items", "controlKind": "CHILD_TABLE", "props": {"childEntity": "expense_reimbursement_item", "columns": ["expense_date", "category_id", "amount", "invoice_type", "invoice_no", "remark"]}}
        ]},
        {"id": "sec_files", "title": "发票影像", "controls": [
          {"fieldCode": "attachment", "controlKind": "ATTACHMENT", "props": {"maxCount": 10, "maxSizeMB": 10}}
        ]},
        {"id": "sec_approval", "title": "审批", "controls": [
          {"fieldCode": "approver_id", "controlKind": "REFERENCE", "props": {"targetEntity": "iam_account", "labelField": "name", "valueField": "id"}},
          {"fieldCode": "status", "controlKind": "ENUM", "readonly": true}
        ]}
      ]
    },
    "detail": {
      "sections": [
        {"id": "sec_base", "title": "基本信息", "controls": [
          {"fieldCode": "title"}, {"fieldCode": "category_id"}, {"fieldCode": "amount"}, {"fieldCode": "status"}
        ]},
        {"id": "sec_items", "title": "费用明细", "controls": [{"fieldCode": "items", "controlKind": "CHILD_TABLE"}]}
      ]
    }
  },
  "linkageRules": [
    {"name": "结束日期不得早于开始日期", "when": {"fieldCode": "expense_end", "operator": "earlierThan", "valueType": "field", "compareField": "expense_start"}, "then": [{"fieldCode": "expense_end", "notice": "结束日期早于开始日期，请修正（提交时阻断）"}]},
    {"name": "大额报销必须指定审批人", "when": {"fieldCode": "amount", "operator": "gt", "value": 5000}, "then": [{"fieldCode": "approver_id", "visible": true, "required": true}]},
    {"name": "审批人不得为申请人本人", "when": {"fieldCode": "approver_id", "operator": "eq", "valueType": "field", "compareField": "applicant"}, "then": [{"fieldCode": "approver_id", "notice": "审批人不能为申请人本人（SoD，提交时阻断）"}]},
    {"name": "达到附件阈值必须有发票影像", "when": {"fieldCode": "amount", "operator": "gte", "value": 200}, "then": [{"fieldCode": "attachment", "visible": true, "required": true}]}
  ],
  "submitRules": [
    {"name": "明细合计一致", "function": "sum", "fields": ["items.amount"], "operator": "eq", "compare": {"valueType": "field", "fieldCode": "amount"}, "verifyType": "ERROR", "message": "费用明细合计必须等于申报金额"},
    {"name": "单行金额下限", "function": "min", "fields": ["items.amount"], "operator": "gte", "compare": {"valueType": "fixed", "value": 0.01}, "verifyType": "ERROR", "message": "费用明细单行金额不得小于 0.01"},
    {"name": "结束日期不得早于开始日期", "function": "compare", "fields": ["expense_end"], "operator": "gte", "compare": {"valueType": "field", "fieldCode": "expense_start"}, "verifyType": "ERROR", "message": "结束日期不得早于开始日期"}
  ],
  "actionBar": [
    {"actionCode": "submit", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "DRAFT"}, "sortOrder": 1},
    {"actionCode": "withdraw", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "SUBMITTED"}, "sortOrder": 2},
    {"actionCode": "approve", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "SUBMITTED"}, "sortOrder": 3},
    {"actionCode": "reject", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "SUBMITTED"}, "sortOrder": 4},
    {"actionCode": "reopen", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "REJECTED"}, "sortOrder": 5},
    {"actionCode": "audit", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "APPROVED"}, "sortOrder": 6},
    {"actionCode": "auditReject", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "APPROVED"}, "sortOrder": 7},
    {"actionCode": "cancel", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "DRAFT"}, "sortOrder": 8},
    {"actionCode": "cancelRejected", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "REJECTED"}, "sortOrder": 9},
    {"actionCode": "confirmPayout", "visibleWhen": {"fieldCode": "status", "operator": "eq", "value": "AUDITED"}, "sortOrder": 10}
  ]
}',
    updated_at = NOW(3)
WHERE tenant_id = 0 AND deleted = 0 AND code = 'tpl_expense_reimbursement_v2';

-- 4.0 金额冻结下沉为平台字段锁（与 0047 运行时实体保持一致，供新实例化实体继承）：
--     status∈审批链时 amount 不可变，由 UiSchemaRuleEnforcer.enforceFieldLock 在记录更新路径统一强制。
UPDATE meta_model_template
SET ui_schema_json = JSON_SET(
    ui_schema_json,
    '$.fieldLockRules',
    CAST('[{"name":"审批链上金额冻结","code":"LOCK-AMT-01","when":{"fieldCode":"status","operator":"in","value":["SUBMITTED","APPROVED","AUDITED","PAID","REJECTED","CANCELLED"]},"fields":["amount"],"message":"已进入审批/支付/结算流程，金额不可修改"}]' AS JSON))
WHERE tenant_id = 0 AND deleted = 0 AND code = 'tpl_expense_reimbursement_v2';

-- ── 5. 校验 ─────────────────────────────────────────────────────────────
SELECT
    (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2')) AS v2_template_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND t.code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2')) AS v2_field_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND t.code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2')
         AND f.field_type IN ('REFERENCE','CHILD_TABLE','ATTACHMENT')) AS relation_field_count,
    (SELECT COUNT(*) FROM meta_model_template_action a
        JOIN meta_model_template t ON t.id = a.template_id AND t.deleted = 0
       WHERE a.deleted = 0 AND t.code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2')) AS v2_action_count,
    (SELECT COUNT(*) FROM meta_model_template_action a
        JOIN meta_model_template t ON t.id = a.template_id AND t.deleted = 0
       WHERE a.deleted = 0 AND t.code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2') AND a.is_escape = 1) AS escape_action_count,
    (SELECT COUNT(*) FROM meta_model_template_action a
        JOIN meta_model_template t ON t.id = a.template_id AND t.deleted = 0
       WHERE a.deleted = 0 AND t.code IN
        ('tpl_expense_reimbursement_v2','tpl_expense_reimbursement_item_v2','tpl_expense_category_v2','tpl_expense_advance_v2') AND a.is_entry = 1) AS entry_action_count,
    (SELECT COUNT(*) FROM meta_model_template
       WHERE deleted = 0 AND code = 'tpl_expense_reimbursement_v2'
         AND ui_schema_json IS NOT NULL AND ui_schema_json <> '') AS ui_asset_count;
