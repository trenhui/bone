-- ----------------------------
-- 测试数据插入
-- ----------------------------
-- 插入数据到 users 表
INSERT INTO users (
  id,
  name,
  role_id,
  create_time,
  create_by,
  update_time,
  update_by,
  deleted
) VALUES
-- 系统管理员（未删除）
(1, 'sys_update', 1, NOW(), 1001, NOW(), 1001, 0),
(5, 'sys_admin', 1, NOW(), 1001, NOW(), 1001, 0),

-- 财务用户（未删除）
(6, 'finance_user', 2, '2024-04-15 09:00:00', 1002, '2024-04-15 09:00:00', 1002, 0),

-- 已删除的测试账号
(7, 'test_deleted', 3, '2024-04-10 14:30:00', 1003, '2024-04-12 16:45:00', 1003, 1),

-- 审计用户（跨部门角色）
(8, 'audit_user', 2, '2024-04-14 11:20:00', 1004, '2024-04-14 11:20:00', 1004, 0),

-- 特殊字符测试账号（包含中文）
(9, '用户_测试', 6, NOW(), 1005, NOW(), 1005, 0),

-- 多角色压力测试
(10, 'stress_test', 2, '2024-04-13 08:00:00', 1006, '2024-04-13 08:00:00', 1006, 0);

-- 插入数据到 roles 表
INSERT INTO roles (id, role_name, description) VALUES
(1, 'ADMIN', 'Administrator role'),
(2, 'USER', 'Standard user role'),
(3, 'GUEST', 'Guest role with limited access'),
-- 新增业务角色
(4, 'FINANCE_MGR', 'Financial operations and reporting access'),
(5, 'HR_ADMIN', 'Employee data management permissions'),
(6, 'READ_ONLY', 'Global read access without write capabilities');


-- 插入系统权限
INSERT INTO sys_permission
    (biz_identity_code, perm_name, perm_code, perm_type, parent_id, path, component, icon, sort_order)
VALUES
    ('pukang', 'Dashboard', 'DASHBOARD', 1, NULL, '/dashboard', 'Dashboard', 'dashboard', 1),
    ('pukang', 'System Settings', 'SYSTEM_SETTINGS', 1, NULL, '/system', 'System', 'setting', 2);

-- 插入用户角色关联
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, (SELECT id FROM roles WHERE role_name = 'ADMIN')),
(2, (SELECT id FROM roles WHERE role_name = 'USER'));

-- 插入字段元数据
INSERT INTO field_metadata
    (tenant_id, app_code, biz_identity_code, entity_type, name, column_name, data_type)
VALUES
    ('100', 'extTest', 'pukang', 'Permission', 'perm_name', 'perm_name', 'STRING'),
    ('100', 'extTest', 'pukang', 'Permission', 'perm_code', 'perm_code', 'STRING'),
    ('100', 'extTest', 'pukang', 'Permission', 'security_level', 'ext_int_01', 'INTEGER'),
    ('100', 'extTest', 'pukang', 'Permission', 'department', 'ext_str_01', 'STRING'),
    ('100', 'extTest', 'pukang', 'Permission', 'approval_required', 'ext_boolean_01', 'BOOLEAN');

-- 插入字段权限
INSERT INTO field_permission (role_code, entity_type, field_name, can_read, can_write) VALUES
('ADMIN', 'Permission', 'perm_name', 1, 1),
('ADMIN', 'Permission', 'perm_code', 1, 1),
('USER', 'Permission', 'perm_name', 1, 0);




-- =========================================================
-- 1. 角色表（10 个全新角色）
-- =========================================================
INSERT INTO roles (id, role_name, description) VALUES
(20000, 'ROOT_ADMIN', '最高级系统管理员'),
(20001, 'SEC_ADMIN', '安全管理员'),
(20002, 'ACCOUNTANT', '会计角色'),
(20003, 'HR_LEAD',   'HR主管'),
(20004, 'DEPT_LEADER','部门领导'),
(20005, 'ENGINEER',  '开发工程师'),
(20006, 'QA',        '测试工程师'),
(20007, 'OP',        '运维工程师'),
(20008, 'VISITOR',   '访客只读'),
(20009, 'CONSULTANT','外部顾问');

-- =========================================================
-- 2. 用户表（50 个全新用户）
-- 为方便演示，role_id 先用固定值，后面再统一建关联
-- =========================================================
INSERT INTO users (id, name, role_id, create_time, create_by, update_time, update_by, deleted) VALUES
(20000, 'Alpha',   20000, NOW(), 20000, NOW(), 20000, 0),
(20001, 'Beta',    20000, NOW(), 20000, NOW(), 20000, 0),
(20002, 'Gamma',   20000, NOW(), 20000, NOW(), 20000, 0),
(20003, 'Delta',   20000, NOW(), 20000, NOW(), 20000, 0),
(20004, 'Epsilon', 20000, NOW(), 20000, NOW(), 20000, 0),

(20005, 'Zeta',    20001, NOW(), 20000, NOW(), 20000, 0),
(20006, 'Eta',     20001, NOW(), 20000, NOW(), 20000, 0),
(20007, 'Theta',   20001, NOW(), 20000, NOW(), 20000, 0),
(20008, 'Iota',    20001, NOW(), 20000, NOW(), 20000, 0),
(20009, 'Kappa',   20001, NOW(), 20000, NOW(), 20000, 0),

(20010, 'Lambda',  20002, NOW(), 20000, NOW(), 20000, 0),
(20011, 'Mu',      20002, NOW(), 20000, NOW(), 20000, 0),
(20012, 'Nu',      20002, NOW(), 20000, NOW(), 20000, 0),
(20013, 'Xi',      20002, NOW(), 20000, NOW(), 20000, 0),
(20014, 'Omicron', 20002, NOW(), 20000, NOW(), 20000, 0),

(20015, 'Pi',      20003, NOW(), 20000, NOW(), 20000, 0),
(20016, 'Rho',     20003, NOW(), 20000, NOW(), 20000, 0),
(20017, 'Sigma',   20003, NOW(), 20000, NOW(), 20000, 0),
(20018, 'Tau',     20003, NOW(), 20000, NOW(), 20000, 0),
(20019, 'Upsilon', 20003, NOW(), 20000, NOW(), 20000, 0),

(20020, 'Phi',     20004, NOW(), 20000, NOW(), 20000, 0),
(20021, 'Chi',     20004, NOW(), 20000, NOW(), 20000, 0),
(20022, 'Psi',     20004, NOW(), 20000, NOW(), 20000, 0),
(20023, 'Omega',   20004, NOW(), 20000, NOW(), 20000, 0),
(20024, 'Aries',   20004, NOW(), 20000, NOW(), 20000, 0),

(20025, 'Taurus',  20005, NOW(), 20000, NOW(), 20000, 0),
(20026, 'Gemini',  20005, NOW(), 20000, NOW(), 20000, 0),
(20027, 'Cancer',  20005, NOW(), 20000, NOW(), 20000, 0),
(20028, 'Leo',     20005, NOW(), 20000, NOW(), 20000, 0),
(20029, 'Virgo',   20005, NOW(), 20000, NOW(), 20000, 0),

(20030, 'Libra',   20006, NOW(), 20000, NOW(), 20000, 0),
(20031, 'Scorpio', 20006, NOW(), 20000, NOW(), 20000, 0),
(20032, 'Sagitt',  20006, NOW(), 20000, NOW(), 20000, 0),
(20033, 'Capri',   20006, NOW(), 20000, NOW(), 20000, 0),
(20034, 'Aquarius',20006, NOW(), 20000, NOW(), 20000, 0),

(20035, 'Pisces',  20007, NOW(), 20000, NOW(), 20000, 0),
(20036, 'Aries2',  20007, NOW(), 20000, NOW(), 20000, 0),
(20037, 'Taurus2', 20007, NOW(), 20000, NOW(), 20000, 0),
(20038, 'Gemini2', 20007, NOW(), 20000, NOW(), 20000, 0),
(20039, 'Cancer2', 20007, NOW(), 20000, NOW(), 20000, 0),

(20040, 'Leo2',    20008, NOW(), 20000, NOW(), 20000, 0),
(20041, 'Virgo2',  20008, NOW(), 20000, NOW(), 20000, 0),
(20042, 'Libra2',  20008, NOW(), 20000, NOW(), 20000, 0),
(20043, 'Scorpio2',20008, NOW(), 20000, NOW(), 20000, 0),
(20044, 'Sagitt2', 20008, NOW(), 20000, NOW(), 20000, 0),

(20045, 'Capri2',  20009, NOW(), 20000, NOW(), 20000, 0),
(20046, 'Aquarius2',20009, NOW(), 20000, NOW(), 20000, 0),
(20047, 'Pisces2',  20009, NOW(), 20000, NOW(), 20000, 0),
(20048, 'Aries3',   20009, NOW(), 20000, NOW(), 20000, 0),
(20049, 'Taurus3',  20009, NOW(), 20000, NOW(), 20000, 0);

-- =========================================================
-- 3. 系统权限表（30 个全新权限）
-- =========================================================
INSERT INTO sys_permission
(id, biz_identity_code, perm_name, perm_code, perm_type, parent_id, path, component, icon, sort_order)
VALUES
(20000, 'core', '系统概览', 'dashboard:view', 1, 0, '/dashboard', 'dashboard/index', 'dashboard', 0),
(20001, 'core', '用户中心', 'uc:index', 1, 0, '/uc', 'uc/index', 'user', 1),
(20002, 'core', '站内信', 'msg:index', 1, 0, '/msg', 'msg/index', 'mail', 2),
(20003, 'core', '系统公告', 'notice:index', 1, 0, '/notice', 'notice/index', 'bullhorn', 3),
(20004, 'core', '文件中心', 'file:center', 1, 0, '/file', 'file/index', 'folder', 4),
(20005, 'core', '在线用户监控', 'monitor:online', 1, 0, '/monitor/online', 'monitor/online', 'online', 5),
(20006, 'core', '服务监控', 'monitor:server', 1, 0, '/monitor/server', 'monitor/server', 'server', 6),
(20007, 'core', '操作日志', 'log:operate', 1, 0, '/log/operate', 'log/operate', 'log', 7),
(20008, 'core', '登录日志', 'log:login', 1, 0, '/log/login', 'log/login', 'log', 8),
(20009, 'core', '错误日志', 'log:error', 1, 0, '/log/error', 'log/error', 'bug', 9),

(20010, 'auth', '角色管理', 'role:manage', 1, 0, '/auth/role', 'auth/role/index', 'role', 10),
(20011, 'auth', '权限管理', 'perm:manage', 1, 0, '/auth/perm', 'auth/perm/index', 'perm', 11),
(20012, 'auth', '菜单管理', 'menu:manage', 1, 0, '/auth/menu', 'auth/menu/index', 'menu', 12),
(20013, 'auth', '字段权限', 'field:perm', 1, 0, '/auth/field', 'auth/field/index', 'field', 13),
(20014, 'auth', '数据权限', 'data:perm', 1, 0, '/auth/data', 'auth/data/index', 'data', 14),

(20015, 'finance', '付款单', 'pay:order', 2, 0, '/finance/pay', 'finance/pay/index', 'pay', 15),
(20016, 'finance', '收款单', 'rec:order', 2, 0, '/finance/rec', 'finance/rec/index', 'wallet', 16),
(20017, 'finance', '发票管理', 'invoice:manage', 2, 0, '/finance/invoice', 'finance/invoice/index', 'invoice', 17),
(20018, 'finance', '财务报表', 'report:finance', 2, 0, '/report/finance', 'report/finance', 'chart', 18),

(20019, 'hr', '员工档案', 'emp:profile', 2, 0, '/hr/emp', 'hr/emp/index', 'idcard', 19),
(20020, 'hr', '薪资管理', 'salary:manage', 2, 0, '/hr/salary', 'hr/salary/index', 'money', 20),
(20021, 'hr', '招聘需求', 'recruit:need', 2, 0, '/hr/recruit', 'hr/recruit/index', 'hire', 21),
(20022, 'hr', '培训计划', 'train:plan', 2, 0, '/hr/train', 'hr/train/index', 'education', 22),

(20023, 'dev', '代码仓库', 'repo:index', 2, 0, '/dev/repo', 'dev/repo/index', 'github', 23),
(20024, 'dev', 'API 文档', 'api:doc', 2, 0, '/dev/api', 'dev/api/index', 'api', 24),
(20025, 'dev', '持续集成', 'ci:index', 2, 0, '/dev/ci', 'dev/ci/index', 'rocket', 25),

(20026, 'test', '测试用例', 'case:index', 2, 0, '/test/case', 'test/case/index', 'case', 26),
(20027, 'test', '缺陷跟踪', 'bug:index', 2, 0, '/test/bug', 'test/bug/index', 'bug', 27),
(20028, 'test', '自动化报告', 'auto:report', 2, 0, '/test/auto', 'test/auto/index', 'report', 28),

(20029, 'ops', '主机管理', 'host:index', 2, 0, '/ops/host', 'ops/host/index', 'server', 29),
(20030, 'ops', '发布单', 'deploy:index', 2, 0, '/ops/deploy', 'ops/deploy/index', 'deploy', 30);

-- =========================================================
-- 4. 用户-角色关联（50 条，一对一）
-- =========================================================
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, u.role_id
FROM users u
WHERE u.id >= 20000;

-- =========================================================
-- 5. 角色-权限关联（每个角色 3 条，共 30 条）
-- =========================================================
INSERT INTO sys_role_permission (role_id, perm_id) VALUES
(20000, 20000), (20000, 20005), (20000, 20006),
(20001, 20007), (20001, 20008), (20001, 20009),
(20002, 20015), (20002, 20016), (20002, 20017),
(20003, 20019), (20003, 20020), (20003, 20021),
(20004, 20010), (20004, 20011), (20004, 20012),
(20005, 20023), (20005, 20024), (20005, 20025),
(20006, 20026), (20006, 20027), (20006, 20028),
(20007, 20029), (20007, 20030), (20007, 20005),
(20008, 20000), (20008, 20001), (20008, 20002),
(20009, 20000), (20009, 20001), (20009, 20004);

-- =========================================================
-- 6. 字段权限表（50 条：10 角色 * 5 个字段 * 不同策略）
-- =========================================================
INSERT INTO field_permission (role_code, entity_type, field_name, can_read, can_write) VALUES
-- User.salary
('ROOT_ADMIN',  'User', 'salary', 1, 1),
('SEC_ADMIN',   'User', 'salary', 1, 0),
('ACCOUNTANT',  'User', 'salary', 1, 1),
('HR_LEAD',     'User', 'salary', 1, 1),
('DEPT_LEADER', 'User', 'salary', 1, 0),
('ENGINEER',    'User', 'salary', 0, 0),
('QA',          'User', 'salary', 0, 0),
('OP',          'User', 'salary', 0, 0),
('VISITOR',     'User', 'salary', 0, 0),
('CONSULTANT',  'User', 'salary', 1, 0),

-- User.email
('ROOT_ADMIN',  'User', 'email', 1, 1),
('SEC_ADMIN',   'User', 'email', 1, 1),
('ACCOUNTANT',  'User', 'email', 1, 0),
('HR_LEAD',     'User', 'email', 1, 1),
('DEPT_LEADER', 'User', 'email', 1, 0),
('ENGINEER',    'User', 'email', 1, 0),
('QA',          'User', 'email', 1, 0),
('OP',          'User', 'email', 1, 0),
('VISITOR',     'User', 'email', 0, 0),
('CONSULTANT',  'User', 'email', 1, 0),

-- Order.amount
('ROOT_ADMIN',  'Order', 'amount', 1, 1),
('SEC_ADMIN',   'Order', 'amount', 1, 0),
('ACCOUNTANT',  'Order', 'amount', 1, 1),
('HR_LEAD',     'Order', 'amount', 0, 0),
('DEPT_LEADER', 'Order', 'amount', 1, 0),
('ENGINEER',    'Order', 'amount', 0, 0),
('QA',          'Order', 'amount', 0, 0),
('OP',          'Order', 'amount', 0, 0),
('VISITOR',     'Order', 'amount', 0, 0),
('CONSULTANT',  'Order', 'amount', 1, 0),

-- Emp.bankAccount
('ROOT_ADMIN',  'Emp', 'bankAccount', 1, 1),
('SEC_ADMIN',   'Emp', 'bankAccount', 1, 0),
('ACCOUNTANT',  'Emp', 'bankAccount', 1, 1),
('HR_LEAD',     'Emp', 'bankAccount', 1, 1),
('DEPT_LEADER', 'Emp', 'bankAccount', 0, 0),
('ENGINEER',    'Emp', 'bankAccount', 0, 0),
('QA',          'Emp', 'bankAccount', 0, 0),
('OP',          'Emp', 'bankAccount', 0, 0),
('VISITOR',     'Emp', 'bankAccount', 0, 0),
('CONSULTANT',  'Emp', 'bankAccount', 0, 0),

-- Emp.contractType
('ROOT_ADMIN',  'Emp', 'contractType', 1, 1),
('SEC_ADMIN',   'Emp', 'contractType', 1, 0),
('ACCOUNTANT',  'Emp', 'contractType', 0, 0),
('HR_LEAD',     'Emp', 'contractType', 1, 1),
('DEPT_LEADER', 'Emp', 'contractType', 1, 0),
('ENGINEER',    'Emp', 'contractType', 0, 0),
('QA',          'Emp', 'contractType', 0, 0),
('OP',          'Emp', 'contractType', 0, 0),
('VISITOR',     'Emp', 'contractType', 0, 0),
('CONSULTANT',  'Emp', 'contractType', 1, 0);


---- 插入测试数据
--INSERT INTO sales_record (category, amount, price, status, create_time, region, product_name, quantity) VALUES
--('电子产品', 1000.00, 500.00, 'ACTIVE', '2023-01-15 10:30:00', '华东', '智能手机', 2),
--('电子产品', 2500.00, 1250.00, 'ACTIVE', '2023-01-16 14:20:00', '华东', '笔记本电脑', 2),
--('电子产品', 800.00, 800.00, 'ACTIVE', '2023-01-17 09:15:00', '华东', '耳机', 1),
--('电子产品', 4500.00, 1500.00, 'INACTIVE', '2023-01-18 16:45:00', '华南', '平板电脑', 3),
--('服装', 300.00, 150.00, 'ACTIVE', '2023-01-15 11:20:00', '华北', '衬衫', 2),
--('服装', 500.00, 250.00, 'ACTIVE', '2023-01-16 13:10:00', '华北', '裤子', 2),
--('服装', 1200.00, 400.00, 'ACTIVE', '2023-01-17 15:30:00', '华南', '外套', 3),
--('服装', 800.00, 200.00, 'INACTIVE', '2023-01-18 10:45:00', '西南', '鞋子', 4),
--('食品', 200.00, 40.00, 'ACTIVE', '2023-01-15 12:30:00', '华东', '零食礼包', 5),
--('食品', 150.00, 30.00, 'ACTIVE', '2023-01-16 14:40:00', '华北', '饮料', 5),
--('食品', 300.00, 60.00, 'ACTIVE', '2023-01-17 16:20:00', '华南', '方便面', 5),
--('食品', 180.00, 36.00, 'INACTIVE', '2023-01-18 11:30:00', '西南', '饼干', 5),
--('电子产品', 3200.00, 1600.00, 'ACTIVE', '2023-02-10 09:30:00', '华南', '游戏机', 2),
--('电子产品', 1800.00, 900.00, 'ACTIVE', '2023-02-11 14:15:00', '西南', '显示器', 2),
--('服装', 600.00, 300.00, 'ACTIVE', '2023-02-10 11:20:00', '华东', '毛衣', 2),
--('服装', 900.00, 450.00, 'ACTIVE', '2023-02-11 15:40:00', '华北', '羽绒服', 2),
--('食品', 250.00, 50.00, 'ACTIVE', '2023-02-10 13:10:00', '华南', '巧克力', 5),
--('食品', 120.00, 24.00, 'ACTIVE', '2023-02-11 16:50:00', '西南', '糖果', 5);