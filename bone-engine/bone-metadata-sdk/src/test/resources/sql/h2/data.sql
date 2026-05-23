
-- 插入数据到 users 表
INSERT INTO users (
  id,
  name,
  role_id,
  created_at,
  created_by,
  updated_at,
  updated_by,
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

-- 分页/聚合测试数据集（与 mysql/data.sql 对齐）
INSERT INTO roles (id, role_name, description) VALUES
(20000, 'ROOT_ADMIN', '最高级系统管理员'),
(20001, 'SEC_ADMIN', '安全管理员'),
(20002, 'ACCOUNTANT', '会计角色'),
(20003, 'HR_LEAD',   'HR主管'),
(20004, 'DEPT_LEADER','部门领导');

INSERT INTO users (id, name, role_id, created_at, created_by, updated_at, updated_by, deleted) VALUES
(20000, 'Alpha',   20000, NOW(), 20000, NOW(), 20000, 0),
(20001, 'Beta',    20000, NOW(), 20000, NOW(), 20000, 0),
(20002, 'Gamma',   20000, NOW(), 20000, NOW(), 20000, 0),
(20003, 'Delta',   20000, NOW(), 20000, NOW(), 20000, 0),
(20004, 'Epsilon', 20000, NOW(), 20000, NOW(), 20000, 0);

