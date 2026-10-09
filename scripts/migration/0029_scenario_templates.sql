-- =============================================================================
-- 0029_scenario_templates.sql
--
-- 目的（PRD v0.33 · W1a T4）：把原型里的「巡检 / 补货 / 访客」三套场景落成平台模板数据
--       （`meta_model_template` + `meta_model_template_field`），使 UC-04「换场景只换数据」
--       在生产可复现；并以 ENUM 字段承载补货 / 访客的「多状态机」（C2 闭环前提）。
--
-- 关联：0028_meta_template_enum.sql（本脚本依赖其新增的 enum_values / validation_rules 列，
--       须先于本脚本执行）；前端 T3（FIELD_TYPES 已加 ENUM、字段向导已支持枚举编辑器）。
--
-- 设计
-- ----------------------
-- · 三套模板均为 **平台层共享**（tenant_id = 0），status = 1（已发布，可实例化）。
-- · 含 6–9 个字段；补货 / 访客的状态字段用 ENUM（JSON 码表）实现 5 态状态机。
-- · 幂等：模板按 code（uk_meta_tpl_code）、字段按 (template_id, code)（uk_meta_tpl_f_code）
--   做 ON DUPLICATE KEY UPDATE，可重复执行。
--
-- 执行（不在此脚本内执行，属 L3 审批项；由 DBA / CI 在目标库运行）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0029_scenario_templates.sql
-- 前置：0028_meta_template_enum.sql 已执行。
-- =============================================================================

USE bone;

-- ── 0. 依赖护栏：0028 未执行则直接报错（列不存在 → MySQL 1054） ──────────
-- 若 0028 未跑，下一行会报 "Unknown column 'enum_values'"，无需额外 SIGNAL。
SELECT enum_values FROM meta_model_template_field LIMIT 0;

-- ── 1. 模板主表（3 套场景） ─────────────────────────────────────────────
INSERT INTO meta_model_template
    (id, tenant_id, code, name, description, category, current_version, status, created_by, created_at, updated_at, deleted, version)
VALUES
    (9120, 0, 'tpl_device_inspection', '设备巡检', '设备定期巡检场景模板：巡检单/设备/结果/状态，开箱即用，实例化后按需扩展字段', '设备', 'v1.0.0', 1, 1, NOW(3), NOW(3), 0, 0),
    (9130, 0, 'tpl_replenishment',     '补货管理', '库存补货场景模板：补货单/商品/数量/优先级/状态机（待补货→已下单→已发货→已收货→已取消）', '供应链', 'v1.0.0', 1, 1, NOW(3), NOW(3), 0, 0),
    (9140, 0, 'tpl_visitor_pass',      '访客通行', '访客登记场景模板：访客/被访人/事由/通行状态机（待审批→已通过→已拒绝→已入场→已离场）', '安防', 'v1.0.0', 1, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description),
    current_version = VALUES(current_version), status = VALUES(status);

-- ── 2. 模板字段集 ───────────────────────────────────────────────────────
-- 列序：(id, tenant_id, template_id, code, name, display_name, field_type,
--        length, is_required, is_unique, default_value, comment, sort_order,
--        enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
INSERT INTO meta_model_template_field
    (id, tenant_id, template_id, code, name, display_name, field_type, length, is_required, is_unique, default_value, comment, sort_order, enum_values, validation_rules, created_by, created_at, updated_at, deleted, version)
VALUES
    -- ── 设备巡检（9120）：7 字段，含 2 个 ENUM ──
    (9121, 0, 9120, 'insp_no',     'inspectionNo', '巡检单号', 'string', 64,  1, 1, NULL, '业务唯一编码', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9122, 0, 9120, 'device_code', 'deviceCode',   '设备编码', 'string', 64,  1, 0, NULL, '被巡检设备编码', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9123, 0, 9120, 'inspector',   'inspector',    '巡检人',   'string', 64,  0, 0, NULL, '执行巡检的人员', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9124, 0, 9120, 'insp_date',   'inspectionDate','巡检日期', 'date',   NULL,1, 0, NULL, '计划/实际巡检日期', 4, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9125, 0, 9120, 'result',      'result',       '巡检结果', 'ENUM',   NULL,1, 0, 'NORMAL', '正常/异常', 5,
        '[{"code":"NORMAL","label":"正常"},{"code":"ABNORMAL","label":"异常"}]', NULL, 1, NOW(3), NOW(3), 0, 0),
    (9126, 0, 9120, 'status',      'status',       '状态',     'ENUM',   NULL,1, 0, 'OPEN', '打开/关闭', 6,
        '[{"code":"OPEN","label":"待处理"},{"code":"CLOSED","label":"已闭环"}]', NULL, 1, NOW(3), NOW(3), 0, 0),
    (9127, 0, 9120, 'remark',      'remark',       '备注',     'text',   NULL,0, 0, NULL, '补充说明', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),

    -- ── 补货管理（9130）：8 字段，含优先级 ENUM + 5 态状态机 ──
    (9131, 0, 9130, 'order_no',   'orderNo',     '补货单号', 'string', 64,  1, 1, NULL, '业务唯一编码', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9132, 0, 9130, 'sku',        'sku',         '商品SKU',  'string', 64,  1, 0, NULL, '补货商品编码', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9133, 0, 9130, 'warehouse',  'warehouse',   '仓库',     'string', 64,  0, 0, NULL, '目标仓库', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9134, 0, 9130, 'qty',        'quantity',    '补货数量', 'integer',NULL, 1, 0, NULL, '建议补货量', 4, NULL, '{"min":1,"max":99999}', 1, NOW(3), NOW(3), 0, 0),
    (9135, 0, 9130, 'priority',   'priority',    '优先级',   'ENUM',   NULL,1, 0, 'MEDIUM', '低/中/高', 5,
        '[{"code":"LOW","label":"低"},{"code":"MEDIUM","label":"中"},{"code":"HIGH","label":"高"}]', NULL, 1, NOW(3), NOW(3), 0, 0),
    (9136, 0, 9130, 'status',     'status',      '补货状态', 'ENUM',   NULL,1, 0, 'PENDING', '5 态状态机', 6,
        '[{"code":"PENDING","label":"待补货"},{"code":"ORDERED","label":"已下单"},{"code":"SHIPPED","label":"已发货"},{"code":"RECEIVED","label":"已收货"},{"code":"CANCELLED","label":"已取消"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9137, 0, 9130, 'expect_date','expectDate',  '期望到货', 'date',   NULL,0, 0, NULL, '期望到货日期', 7, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9138, 0, 9130, 'remark',     'remark',      '备注',     'text',   NULL,0, 0, NULL, '补充说明', 8, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),

    -- ── 访客通行（9140）：8 字段，含事由 ENUM + 5 态通行状态机 ──
    (9141, 0, 9140, 'visitor_name','visitorName', '访客姓名', 'string', 64,  1, 0, NULL, '访客真实姓名', 1, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9142, 0, 9140, 'id_no',      'idNo',        '证件号',   'string', 32,  1, 0, NULL, '身份证/护照号', 2, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9143, 0, 9140, 'host',       'host',        '被访人',   'string', 64,  1, 0, NULL, '被访员工姓名', 3, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9144, 0, 9140, 'reason',     'reason',      '来访事由', 'ENUM',   NULL,1, 0, 'MEETING', '会议/配送/维保/其他', 4,
        '[{"code":"MEETING","label":"会议"},{"code":"DELIVERY","label":"配送"},{"code":"MAINTENANCE","label":"维保"},{"code":"OTHER","label":"其他"}]', NULL, 1, NOW(3), NOW(3), 0, 0),
    (9145, 0, 9140, 'visit_date', 'visitDate',   '来访日期', 'date',   NULL,1, 0, NULL, '计划来访日期', 5, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9146, 0, 9140, 'expire_at',  'expireAt',    '通行截止', 'datetime',NULL,0, 0, NULL, '通行权限截止时刻', 6, NULL, NULL, 1, NOW(3), NOW(3), 0, 0),
    (9147, 0, 9140, 'status',     'status',      '通行状态', 'ENUM',   NULL,1, 0, 'PENDING', '5 态状态机', 7,
        '[{"code":"PENDING","label":"待审批"},{"code":"APPROVED","label":"已通过"},{"code":"REJECTED","label":"已拒绝"},{"code":"CHECKED_IN","label":"已入场"},{"code":"CHECKED_OUT","label":"已离场"}]',
        NULL, 1, NOW(3), NOW(3), 0, 0),
    (9148, 0, 9140, 'plate_no',   'plateNo',     '车牌号',   'string', 20,  0, 0, NULL, '来访车辆车牌', 8, NULL, NULL, 1, NOW(3), NOW(3), 0, 0)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name), is_required = VALUES(is_required),
    is_unique = VALUES(is_unique), default_value = VALUES(default_value),
    comment = VALUES(comment), sort_order = VALUES(sort_order),
    enum_values = VALUES(enum_values), validation_rules = VALUES(validation_rules);

-- ── 3. 校验 ─────────────────────────────────────────────────────────────
SELECT
    (SELECT COUNT(*) FROM meta_model_template WHERE deleted = 0 AND code IN ('tpl_device_inspection','tpl_replenishment','tpl_visitor_pass')) AS template_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND t.code IN ('tpl_device_inspection','tpl_replenishment','tpl_visitor_pass')) AS field_count,
    (SELECT COUNT(*) FROM meta_model_template_field f
        JOIN meta_model_template t ON t.id = f.template_id AND t.deleted = 0
       WHERE f.deleted = 0 AND f.field_type = 'ENUM') AS enum_field_count;
