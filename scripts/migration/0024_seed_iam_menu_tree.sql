-- ============================================================
-- 0024_seed_iam_menu_tree.sql
-- IAM 菜单/按钮权限点种子：把前端硬编码的侧边栏与操作按钮搬到数据库，
-- 使「菜单可见性」与「按钮可见性」都可由 IAM 按租户 + 角色授权驱动。
--
-- 背景（2026-10-08 实测）
-- ----------------------
-- 1) 前端侧边栏是写死的：`bone-shell/src/App.tsx` 里一份 `STATIC_MENU` 常量
--    （11 个分组 / 约 60 个叶子），`enabled: true` 全开、没有任何 permission 字段。
--    任何人登录后看到的一模一样 —— 与租户无关、与角色无关。
-- 2) `iam_menu` 表里有权限、排序、类型三个字段，却几乎是空表：开发库 16 行里
--    只有 3 条真实菜单（平台控制台 / 租户运营 / 元数据建模），其余全是 E2E 残留。
--    也就是说动态菜单的**数据结构早就就绪，缺的是数据**。
-- 3) `/menus/current` 的后端过滤逻辑（租户 + 权限码）已经写完且正确，但因为
--    (a) 挂了 `iam:menus:read` 管理面码，(b) 表是空的，前端永远拿不到非空结果，
--    只能静默回退 STATIC_MENU。
--
-- 本脚本补齐第 2 点，让第 3 点的既有逻辑真正生效（第 1 点由前端同步改造承接）。
--
-- 关键设计
-- --------
-- · **path 采用 `path#hash` 形态**（如 `/iam#/accounts`）。微应用的页内路由是 HashRouter，
--   只有 path 无法区分同一微应用下的不同页面；一个字段表达完整位置，前端按 `#` 拆分即可，
--   避免「菜单表再开一列 hash」这种只服务前端的冗余建模。
-- · **每个租户一份**：菜单 must 支持租户自定义（租户管理员在 IAM「菜单管理」里改自己的树），
--   所以种子按 `iam_tenant` 展开。若只播给平台租户(0)靠继承兜底，会出现悬崖：
--   租户管理员一旦在 UI 里新建任意一条菜单，继承就失效、整棵树崩塌。
-- · **按钮 as type=2 节点**：`iam_menu.type` 本就定义 0-目录 / 1-菜单 / 2-按钮，
--   此前 type=2 从未有数据。这里按「分组-菜单-动作」给每条写操作登记一个权限点，
--   使「某角色能不能点某按钮」变成 IAM 里可配置的一等公民。
-- · **幂等**：按 (tenant_id, name, path) 判存在，可重复执行；不用 INSERT IGNORE
--   （IGNORE 会把其他约束错误一起吞掉，见 0020 RUNBOOK §教训）。
-- · **id 号段**：`910000000000000000 + 租户序号*10000 + 行序号`。
--   刻意避开雪花号段（现网最大 763406802694963200）与 0016 用的 910...000101。
--
-- 依据
-- ----
--   bone-init.sql:231 iam_menu DDL（type 注释 0-目录 1-菜单 2-按钮）
--   packages/shared-types/src/bonePermissionCodes.ts 权限码目录
--   apps/bone-shell/src/App.tsx:230 STATIC_MENU（本脚本的行清单与其 1:1 对齐）
--   MenuApplicationService#current 的租户 + 权限码双重过滤
--
-- 前置：bone-init.sql 已执行（iam_menu / iam_tenant 已建）。
-- 执行：mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0024_seed_iam_menu_tree.sql
-- ============================================================

USE bone;

-- ---------- 阶段 0：备份 ----------
-- 只 CREATE ... SELECT，绝不先 DROP：备份表已存在说明上一次执行过，保留最早那份。
CREATE TABLE IF NOT EXISTS bak_iam_menu_0024 AS SELECT * FROM iam_menu;

-- ---------- 阶段 1：种子模板（与租户无关，仅平台租户视角的一份模板） ----------
DROP TABLE IF EXISTS tmp_menu_seed_0024;
CREATE TABLE tmp_menu_seed_0024 (
    seq          INT          NOT NULL COMMENT '行序号，参与 id 计算',
    ref_key      VARCHAR(64)  NOT NULL COMMENT '本脚本内的引用键，仅用于父子链接',
    parent_ref   VARCHAR(64)  NULL     COMMENT '父节点 ref_key；NULL = 根节点',
    node_name    VARCHAR(200) NOT NULL,
    node_path    VARCHAR(500) NULL,
    node_icon    VARCHAR(200) NULL     COMMENT 'antd 图标组件名，前端按名映射',
    node_order   INT          NOT NULL DEFAULT 0,
    node_perm    VARCHAR(200) NULL     COMMENT '权限标识；NULL = 该租户所有成员可见',
    node_type    TINYINT      NOT NULL DEFAULT 1 COMMENT '0-目录 1-菜单 2-按钮',
    PRIMARY KEY (seq)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_menu_seed_0024
    (seq, ref_key, parent_ref, node_name,     node_path,                        node_icon,                  node_order, node_perm,                       node_type) VALUES
-- 顶层：首页仪表盘（叶子，直接用 /）
(   1, 'root_home',   NULL,         '首页仪表盘',   '/',                              'DashboardOutlined',            1, 'sys:console:read',                 1),
-- IAM · 组织与成员
(   2, 'g_iam_org',   NULL,         '组织与成员',   NULL,                             'ApartmentOutlined',            2, NULL,                               0),
(   3, 'm_depts',     'g_iam_org',  '组织机构',     '/iam#/organizations',            'ApartmentOutlined',            1, 'iam:depts:read',                   1),
(   4, 'm_accounts',  'g_iam_org',  '用户管理',     '/iam#/accounts',                 'UserOutlined',                 2, 'iam:accounts:read',                1),
-- IAM · 权限与角色
(   5, 'g_iam_perm',  NULL,         '权限与角色',   NULL,                             'SafetyCertificateOutlined',    3, NULL,                               0),
(   6, 'm_roles',     'g_iam_perm', '角色管理',     '/iam#/roles',                    'TeamOutlined',                 1, 'iam:roles:read',                   1),
(   7, 'm_perms',     'g_iam_perm', '权限管理',     '/iam#/permissions',              'SafetyCertificateOutlined',    2, 'iam:permissions:read',             1),
(   8, 'm_menus',     'g_iam_perm', '菜单管理',     '/iam#/menus',                    'MenuOutlined',                 3, 'iam:menus:read',                   1),
(   9, 'm_apps',      'g_iam_perm', '应用管理',     '/iam#/apps',                     'AppstoreOutlined',             4, 'iam:apps:read',                    1),
-- IAM · 安全与审计
(  10, 'g_iam_audit', NULL,         '安全与审计',   NULL,                             'AuditOutlined',                4, NULL,                               0),
(  11, 'm_tenants',   'g_iam_audit','租户管理',     '/iam#/tenants',                  'PartitionOutlined',            1, 'iam:tenants:read',                 1),
(  12, 'm_audit',     'g_iam_audit','审计日志',     '/iam#/audit-logs',               'AuditOutlined',                2, 'iam:audit:read',                   1),
(  13, 'm_auditcfg',  'g_iam_audit','审计设置',     '/iam#/audit-settings',           'SettingOutlined',              3, 'iam:audit:write',                  1),
-- 元数据 · 业务建模
(  14, 'g_metadata',  NULL,         '业务建模',     NULL,                             'DatabaseOutlined',             5, NULL,                               0),
(  15, 'm_meta_wb',   'g_metadata', '建模工作台',   '/metadata#/apps',                'AppstoreOutlined',             1, 'metadata:model:read',              1),
(  16, 'm_meta_ent',  'g_metadata', '模型管理',     '/metadata#/entities',            'ApiOutlined',                  2, 'metadata:model:read',              1),
(  17, 'm_meta_rel',  'g_metadata', '关系管理',     '/metadata#/relations',           'BranchesOutlined',             3, 'metadata:model:read',              1),
(  18, 'm_meta_rt',   'g_metadata', '运行时数据',   '/metadata#/runtime',             'ThunderboltOutlined',          4, 'metadata:runtime:read',            1),
-- 主数据
(  19, 'g_mdm',       NULL,         '主数据管理',   NULL,                             'ClusterOutlined',              6, NULL,                               0),
(  20, 'm_mdm_wb',    'g_mdm',      '域工作台',     '/masterdata#/workbench',         'ClusterOutlined',              1, 'masterdata:entities:read',         1),
(  21, 'm_mdm_ent',   'g_mdm',      '主数据模型',   '/masterdata#/entities',          'ApiOutlined',                  2, 'masterdata:entities:read',         1),
(  22, 'm_mdm_fld',   'g_mdm',      '字段管理',     '/masterdata#/fields',            'OrderedListOutlined',          3, 'masterdata:entities:read',         1),
(  23, 'm_mdm_cat',   'g_mdm',      '分类管理',     '/masterdata#/categories',        'ApartmentOutlined',            4, 'masterdata:categories:read',       1),
(  24, 'm_mdm_tpl',   'g_mdm',      '模板管理',     '/masterdata#/templates',         'ProfileOutlined',              5, 'masterdata:templates:read',        1),
(  25, 'm_mdm_rec',   'g_mdm',      '记录管理',     '/masterdata#/records',           'FileTextOutlined',             6, 'masterdata:records:read',          1),
(  26, 'm_mdm_ref',   'g_mdm',      '参考数据',     '/masterdata#/reference-sets',    'UnorderedListOutlined',        7, 'masterdata:reference:read',        1),
(  27, 'm_mdm_rule',  'g_mdm',      '质量规则',     '/masterdata#/rules',             'ReconciliationOutlined',       8, 'masterdata:quality:write',         1),
(  28, 'm_mdm_qr',    'g_mdm',      '质量结果',     '/masterdata#/quality-results',   'AlertOutlined',                9, 'masterdata:quality:write',         1),
(  29, 'm_mdm_qi',    'g_mdm',      '质量问题',     '/masterdata#/quality-issues',    'AuditOutlined',               10, 'masterdata:quality:write',         1),
(  30, 'm_mdm_gov',   'g_mdm',      '治理看板',     '/masterdata#/governance',        'DashboardOutlined',           11, 'masterdata:governance:write',      1),
-- 交易
(  31, 'g_com',       NULL,         '交易管理',     NULL,                             'ShoppingOutlined',             7, NULL,                               0),
(  32, 'm_orders',    'g_com',      '订单管理',     '/commerce#/orders',              'ProfileOutlined',              1, 'order:orders:read',                1),
(  33, 'm_payments',  'g_com',      '支付管理',     '/commerce#/payments',            'TransactionOutlined',          2, 'order:payment:read',               1),
(  34, 'm_channels',  'g_com',      '渠道管理',     '/commerce#/channels',            'DeploymentUnitOutlined',       3, 'commerce:channel:read',            1),
(  35, 'm_chprod',    'g_com',      '商品上架',     '/commerce#/channel-products',    'CloudUploadOutlined',          4, 'commerce:product:read',            1),
(  36, 'm_inv',       'g_com',      '库存管理',     '/commerce#/inventories',         'InboxOutlined',                5, 'commerce:inventory:read',          1),
(  37, 'm_buyers',    'g_com',      '买家映射',     '/commerce#/channel-buyers',      'TeamOutlined',                 6, 'commerce:channel-buyer:read',      1),
(  38, 'm_bcast',     'g_com',      '广播任务',     '/commerce#/broadcast-tasks',     'ThunderboltOutlined',          7, 'commerce:broadcast:read',          1),
(  39, 'm_ship',      'g_com',      '发货物流',     '/commerce#/shipments',           'CarOutlined',                  8, 'commerce:shipment:read',           1),
-- 集成
(  40, 'g_int',       NULL,         '集成管理',     NULL,                             'LinkOutlined',                 8, NULL,                               0),
(  41, 'm_conn',      'g_int',      '连接器管理',   '/integration#/connectors',       'NodeIndexOutlined',            1, 'integration:connectors:read',      1),
(  42, 'm_flows',     'g_int',      '流程编排',     '/integration#/flows',            'ControlOutlined',              2, 'integration:flows:read',           1),
(  43, 'm_monitor',   'g_int',      '运行监控',     '/integration#/monitor',          'LineChartOutlined',            3, 'integration:flows:read',           1),
-- 扩展
(  44, 'g_ext',       NULL,         '扩展管理',     NULL,                             'AppstoreOutlined',             9, NULL,                               0),
(  45, 'm_points',    'g_ext',      '扩展点目录',   '/extension#/points',             'NodeCollapseOutlined',         1, 'extension:points:read',            1),
(  46, 'm_plugins',   'g_ext',      '插件仓库',     '/extension#/plugins',            'UnorderedListOutlined',        2, 'extension:plugins:read',           1),
(  47, 'm_deploy',    'g_ext',      '部署管理',     '/extension#/deploy',             'CloudServerOutlined',          3, 'extension:plugins:deploy',         1),
(  48, 'm_graph',     'g_ext',      '依赖图谱',     '/extension#/graph',              'BranchesOutlined',             4, 'extension:plugins:read',           1),
(  49, 'm_market',    'g_ext',      '低代码市场',   '/extension#/market',             'CoffeeOutlined',               5, 'extension:marketplace:install',    1),
(  50, 'm_extlogs',   'g_ext',      '运行日志',     '/extension#/logs',               'ProfileOutlined',              6, 'extension:observe:read',           1),
-- 代码生成
(  51, 'g_gen',       NULL,         '代码生成',     NULL,                             'CodeOutlined',                10, NULL,                               0),
(  52, 'm_ds',        'g_gen',      '数据源管理',   '/generator#/datasources',        'DatabaseOutlined',             1, 'generator:datasources:write',      1),
(  53, 'm_gen',       'g_gen',      '代码生成',     '/generator#/generate',           'CodeOutlined',                 2, 'generator:codegen:write',          1),
(  54, 'm_gentpl',    'g_gen',      '代码生成模板', '/generator#/templates',          'FileTextOutlined',             3, 'generator:templates:write',        1),
(  55, 'm_genhist',   'g_gen',      '生成历史',     '/generator#/history',            'HistoryOutlined',              4, 'generator:codegen:write',          1),
-- 系统管理
(  56, 'g_sys',       NULL,         '系统管理',     NULL,                             'SettingOutlined',             11, NULL,                               0),
(  57, 'm_cfg',       'g_sys',      '系统配置',     '/system#/config',                'ControlOutlined',              1, 'sys:config:write',                 1),
(  58, 'm_alerts',    'g_sys',      '监控告警',     '/system#/alerts',                'AlertOutlined',                2, 'sys:alert:read',                   1),
(  59, 'm_logs',      'g_sys',      '日志管理',     '/system#/logs',                  'CloudOutlined',                3, 'sys:log:write',                    1),
(  60, 'm_k8s',       'g_sys',      'K8s 部署',     '/system#/k8s',                   'CloudServerOutlined',          4, 'sys:ops:execute',                  1),
(  61, 'm_dict',      'g_sys',      '字典管理',     '/system#/dict',                  'OrderedListOutlined',          5, 'sys:dict:write',                   1),
(  62, 'm_sched',     'g_sys',      '定时任务',     '/system#/schedule',              'ClockCircleOutlined',          6, 'sys:schedule:write',               1),
-- ---------- type=2 按钮权限点：每条写操作一个点，供「角色授权」与前端按钮门禁消费 ----------
-- 命名 `<分组>-<菜单>-<动作>`：保证同租户内 name 唯一，且在 IAM 菜单管理页里可读。
( 101, 'b_depts_add',    'm_depts',    '组织与成员-组织机构-新增',        NULL, NULL, 1, 'iam:depts:write',                 2),
( 102, 'b_depts_del',    'm_depts',    '组织与成员-组织机构-删除',        NULL, NULL, 2, 'iam:depts:write',                 2),
( 103, 'b_acc_add',      'm_accounts', '组织与成员-用户管理-新增',        NULL, NULL, 1, 'iam:accounts:write',              2),
( 104, 'b_acc_edit',     'm_accounts', '组织与成员-用户管理-编辑',        NULL, NULL, 2, 'iam:accounts:write',              2),
( 105, 'b_acc_del',      'm_accounts', '组织与成员-用户管理-删除',        NULL, NULL, 3, 'iam:accounts:write',              2),
( 106, 'b_acc_toggle',   'm_accounts', '组织与成员-用户管理-启停',        NULL, NULL, 4, 'iam:accounts:write',              2),
( 107, 'b_role_add',     'm_roles',    '权限与角色-角色管理-新增',        NULL, NULL, 1, 'iam:roles:write',                 2),
( 108, 'b_role_grant',   'm_roles',    '权限与角色-角色管理-分配权限',    NULL, NULL, 2, 'iam:roles:write',                 2),
( 109, 'b_role_del',     'm_roles',    '权限与角色-角色管理-删除',        NULL, NULL, 3, 'iam:roles:write',                 2),
( 110, 'b_perm_add',     'm_perms',    '权限与角色-权限管理-新增',        NULL, NULL, 1, 'iam:permissions:write',           2),
( 111, 'b_perm_del',     'm_perms',    '权限与角色-权限管理-删除',        NULL, NULL, 2, 'iam:permissions:write',           2),
( 112, 'b_menu_add',     'm_menus',    '权限与角色-菜单管理-新增',        NULL, NULL, 1, 'iam:menus:write',                 2),
( 113, 'b_menu_del',     'm_menus',    '权限与角色-菜单管理-删除',        NULL, NULL, 2, 'iam:menus:write',                 2),
( 114, 'b_app_add',      'm_apps',     '权限与角色-应用管理-新增',        NULL, NULL, 1, 'iam:apps:write',                   2),
( 115, 'b_tenant_add',   'm_tenants',  '安全与审计-租户管理-新增',        NULL, NULL, 1, 'iam:tenants:write',                2),
( 116, 'b_tenant_toggle','m_tenants',  '安全与审计-租户管理-启停',        NULL, NULL, 2, 'iam:tenants:write',                2),
( 117, 'b_audit_cfg',    'm_auditcfg', '安全与审计-审计设置-保存',        NULL, NULL, 1, 'iam:audit:write',                  2),
( 118, 'b_meta_ent_add', 'm_meta_ent', '业务建模-模型管理-新增',          NULL, NULL, 1, 'metadata:model:write',             2),
( 119, 'b_meta_pub',     'm_meta_ent', '业务建模-模型管理-发布',          NULL, NULL, 2, 'metadata:publish',                 2),
( 120, 'b_meta_rel',     'm_meta_rel', '业务建模-关系管理-维护',          NULL, NULL, 1, 'metadata:model:write',             2),
( 121, 'b_meta_rt',      'm_meta_rt',  '业务建模-运行时数据-写入',        NULL, NULL, 1, 'metadata:runtime:write',           2),
( 122, 'b_mdm_ent',      'm_mdm_ent',  '主数据管理-主数据模型-新增',      NULL, NULL, 1, 'masterdata:entities:write',        2),
( 123, 'b_mdm_fld',      'm_mdm_fld',  '主数据管理-字段管理-新增',        NULL, NULL, 1, 'masterdata:entities:write',        2),
( 124, 'b_mdm_cat',      'm_mdm_cat',  '主数据管理-分类管理-新增',        NULL, NULL, 1, 'masterdata:categories:write',      2),
( 125, 'b_mdm_tpl',      'm_mdm_tpl',  '主数据管理-模板管理-新增',        NULL, NULL, 1, 'masterdata:templates:write',       2),
( 126, 'b_mdm_tpl_inst', 'm_mdm_tpl',  '主数据管理-模板管理-实例化',      NULL, NULL, 2, 'masterdata:templates:instantiate', 2),
( 127, 'b_mdm_rec_add',  'm_mdm_rec',  '主数据管理-记录管理-新增',        NULL, NULL, 1, 'masterdata:records:write',         2),
( 128, 'b_mdm_rec_appr', 'm_mdm_rec',  '主数据管理-记录管理-审批',        NULL, NULL, 2, 'masterdata:records:approve',       2),
( 129, 'b_mdm_ref',      'm_mdm_ref',  '主数据管理-参考数据-维护',        NULL, NULL, 1, 'masterdata:reference:write',       2),
( 130, 'b_mdm_rule',     'm_mdm_rule', '主数据管理-质量规则-执行',        NULL, NULL, 1, 'masterdata:quality:write',         2),
( 131, 'b_mdm_gov',      'm_mdm_gov',  '主数据管理-治理看板-治理操作',    NULL, NULL, 1, 'masterdata:governance:write',      2),
( 132, 'b_order_edit',   'm_orders',   '交易管理-订单管理-编辑',          NULL, NULL, 1, 'order:orders:write',               2),
( 133, 'b_pay_refund',   'm_payments', '交易管理-支付管理-退款',          NULL, NULL, 1, 'order:payment:write',              2),
( 134, 'b_ch_edit',      'm_channels', '交易管理-渠道管理-维护',          NULL, NULL, 1, 'commerce:channel:write',           2),
( 135, 'b_prod_list',    'm_chprod',   '交易管理-商品上架-上架',          NULL, NULL, 1, 'commerce:product:write',           2),
( 136, 'b_inv_adj',      'm_inv',      '交易管理-库存管理-调整',          NULL, NULL, 1, 'commerce:inventory:write',         2),
( 137, 'b_buyer',        'm_buyers',   '交易管理-买家映射-改绑',          NULL, NULL, 1, 'commerce:channel-buyer:write',     2),
( 138, 'b_bcast',        'm_bcast',    '交易管理-广播任务-重试',          NULL, NULL, 1, 'commerce:broadcast:write',         2),
( 139, 'b_ship',         'm_ship',     '交易管理-发货物流-发货',          NULL, NULL, 1, 'commerce:shipment:write',          2),
( 140, 'b_conn',         'm_conn',     '集成管理-连接器管理-维护',        NULL, NULL, 1, 'integration:connectors:write',     2),
( 141, 'b_flow_save',    'm_flows',    '集成管理-流程编排-保存',          NULL, NULL, 1, 'integration:flows:write',          2),
( 142, 'b_flow_run',     'm_flows',    '集成管理-流程编排-执行',          NULL, NULL, 2, 'integration:executions:write',     2),
( 143, 'b_point',        'm_points',   '扩展管理-扩展点目录-维护',        NULL, NULL, 1, 'extension:points:write',           2),
( 144, 'b_plugin',       'm_plugins',  '扩展管理-插件仓库-维护',          NULL, NULL, 1, 'extension:plugins:write',          2),
( 145, 'b_plugin_deploy','m_plugins',  '扩展管理-插件仓库-部署',          NULL, NULL, 2, 'extension:plugins:deploy',         2),
( 146, 'b_plugin_bind',  'm_plugins',  '扩展管理-插件仓库-绑定',          NULL, NULL, 3, 'extension:plugins:bind',           2),
( 147, 'b_publish',      'm_deploy',   '扩展管理-部署管理-生效切换',      NULL, NULL, 1, 'extension:runtime:publish',        2),
( 148, 'b_market',       'm_market',   '扩展管理-低代码市场-安装',        NULL, NULL, 1, 'extension:marketplace:install',    2),
( 149, 'b_ds_sync',      'm_ds',       '代码生成-数据源管理-同步',        NULL, NULL, 1, 'generator:datasources:sync',       2),
( 150, 'b_gen_run',      'm_gen',      '代码生成-代码生成-提交任务',      NULL, NULL, 1, 'generator:codegen:write',          2),
( 151, 'b_gentpl',       'm_gentpl',   '代码生成-代码生成模板-维护',      NULL, NULL, 1, 'generator:templates:write',        2),
( 152, 'b_cfg',          'm_cfg',      '系统管理-系统配置-保存',          NULL, NULL, 1, 'sys:config:write',                 2),
( 153, 'b_alert',        'm_alerts',   '系统管理-监控告警-处理',          NULL, NULL, 1, 'sys:alert:write',                  2),
( 154, 'b_log',          'm_logs',     '系统管理-日志管理-清理',          NULL, NULL, 1, 'sys:log:write',                    2),
( 155, 'b_ops',          'm_k8s',      '系统管理-K8s 部署-执行',          NULL, NULL, 1, 'sys:ops:execute',                  2),
( 156, 'b_dict',         'm_dict',     '系统管理-字典管理-维护',          NULL, NULL, 1, 'sys:dict:write',                   2),
( 157, 'b_sched',        'm_sched',    '系统管理-定时任务-维护',          NULL, NULL, 1, 'sys:schedule:write',               2);

-- ---------- 阶段 2：目标租户（去重后的全部未软删租户） ----------
DROP TABLE IF EXISTS tmp_seed_tenant_0024;
CREATE TABLE tmp_seed_tenant_0024 (
    idx       INT   NOT NULL PRIMARY KEY,
    tenant_id BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ⚠ 平台租户 0 是**虚拟租户**，`iam_tenant` 表里并没有 id=0 的行（实测 tenants=22 全为业务租户）。
-- 只从 iam_tenant 取集合会把平台租户漏掉 —— 而平台租户恰恰是这份模板的母本：
-- 漏了它，所有平台管理员登录后看不到任何菜单。此处显式 UNION 补上并占用 idx=0。
INSERT INTO tmp_seed_tenant_0024 (idx, tenant_id)
SELECT 0, 0
UNION ALL
SELECT ROW_NUMBER() OVER (ORDER BY id), id
  FROM iam_tenant
 WHERE deleted = 0 AND id <> 0;

-- ---------- 阶段 3：按行计算出待插入的计划（含每租户唯一 id） ----------
DROP TABLE IF EXISTS tmp_menu_plan_0024;
CREATE TABLE tmp_menu_plan_0024 AS
SELECT
    910000000000000000 + (t.idx * 10000) + s.seq  AS new_id,
    t.tenant_id                                   AS tenant_id,
    s.ref_key                                     AS ref_key,
    s.parent_ref                                  AS parent_ref,
    s.node_name                                   AS node_name,
    s.node_path                                   AS node_path,
    s.node_icon                                   AS node_icon,
    s.node_order                                  AS node_order,
    s.node_perm                                   AS node_perm,
    s.node_type                                   AS node_type
FROM tmp_menu_seed_0024 s
CROSS JOIN tmp_seed_tenant_0024 t;

-- MySQL 不允许在同一语句里两次引用同一张临时/派生表做自连接，
-- 因此复制一份专门给「父节点定位」用。
DROP TABLE IF EXISTS tmp_menu_plan_parent_0024;
CREATE TABLE tmp_menu_plan_parent_0024 AS SELECT * FROM tmp_menu_plan_0024;

-- ---------- 阶段 4：插入菜单节点（幂等：按 租户 + 名称 + 路径 判存在） ----------
INSERT INTO iam_menu
    (id, tenant_id, name, parent_id, path, icon, order_no, permission, type, created_by, updated_by)
SELECT
    p.new_id, p.tenant_id, p.node_name, NULL, p.node_path, p.node_icon,
    p.node_order, p.node_perm, p.node_type, NULL, NULL
FROM tmp_menu_plan_0024 p
WHERE NOT EXISTS (
    SELECT 1 FROM iam_menu m
    WHERE m.tenant_id = p.tenant_id
      AND m.name      = p.node_name
      AND IFNULL(m.path, '\0') = IFNULL(p.node_path, '\0')
      AND m.deleted = 0
);

-- ---------- 阶段 5：回填父节点挂接 ----------
UPDATE iam_menu m
JOIN tmp_menu_plan_0024        p  ON p.new_id    = m.id
JOIN tmp_menu_plan_parent_0024 pp ON pp.tenant_id = p.tenant_id
                                  AND pp.ref_key   = p.parent_ref
SET m.parent_id = pp.new_id
WHERE m.id >= 910000000000000000
  AND p.parent_ref IS NOT NULL
  AND m.parent_id IS NULL;

-- ---------- 阶段 6：清理中间表 ----------
DROP TABLE IF EXISTS tmp_menu_seed_0024;
DROP TABLE IF EXISTS tmp_seed_tenant_0024;
DROP TABLE IF EXISTS tmp_menu_plan_0024;
DROP TABLE IF EXISTS tmp_menu_plan_parent_0024;

-- ---------- 阶段 7：校验 ----------
-- 用 SIGNAL 让「插 0 条但 EXIT 0」不再被误判为成功（见 0016 教训）。
-- 期望值针对**本脚本的种子集合**（157 行 × 租户数），不校验表总数 ——
-- 表里还有历史上遗留的 E2E 菜单，本脚本不删他人数据。
DROP PROCEDURE IF EXISTS sp_verify_0024;
DELIMITER $$
CREATE PROCEDURE sp_verify_0024()
BEGIN
    DECLARE v_tenant INT DEFAULT 0;
    DECLARE v_seed   INT DEFAULT 0;
    DECLARE v_expect INT DEFAULT 0;
    DECLARE v_actual INT DEFAULT 0;
    DECLARE v_orphan INT DEFAULT 0;

    -- 租户数 = iam_tenant 里的业务租户 + 平台租户 0（虚拟，不在表内）
    SELECT COUNT(*) INTO v_tenant FROM iam_tenant WHERE deleted = 0 AND id <> 0;
    SET v_tenant = v_tenant + 1;
    SELECT COUNT(*) INTO v_seed
      FROM iam_menu
     WHERE tenant_id = 0
       AND id BETWEEN 910000000000000000 AND 910000000009999999
       AND deleted = 0;

    -- 期望值 = 种子表行数：62 行目录/菜单(seq 1~62) + 57 行按钮 type=2(seq 101~157) = 119
    -- （勿凭感觉填：首次填了 157 就被 SIGNAL 抓出来 —— 数据是对的，错的是期望值）
    SET v_expect = 119;

    IF v_seed <> v_expect THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0024 校验失败：平台租户的种子菜单行数不符，检查阶段 4 的 NOT EXISTS 判定';
    END IF;

    -- 每个租户都应有完整一份；少一份说明租户集合生成有问题
    SELECT COUNT(DISTINCT tenant_id) INTO v_actual
      FROM iam_menu
     WHERE id BETWEEN 910000000000000000 AND 910000000009999999
       AND deleted = 0;
    IF v_actual <> v_tenant THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0024 校验失败：存在未播种菜单的租户，检查阶段 2 的租户集合';
    END IF;

    -- 非根节点必须挂上父；parent_ref 指向不存在的行会留下 NULL parent_id
    SELECT COUNT(*) INTO v_orphan
      FROM iam_menu
     WHERE id BETWEEN 910000000000000000 AND 910000000009999999
       AND deleted = 0
       AND parent_id IS NULL
       AND name NOT IN ('首页仪表盘', '组织与成员', '权限与角色', '安全与审计', '业务建模',
                        '主数据管理', '交易管理', '集成管理', '扩展管理', '代码生成', '系统管理');
    IF v_orphan <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0024 校验失败：存在未挂接父节点的子节点，检查阶段 5';
    END IF;

    SELECT v_tenant AS tenant_count, v_seed AS seed_rows, v_orphan AS orphan_rows;
END$$
DELIMITER ;

CALL sp_verify_0024();
DROP PROCEDURE sp_verify_0024;
