-- 检查 iam_account_role 表的数据
SELECT * FROM iam_account_role;

-- 检查 iam_role 表的数据
SELECT * FROM iam_role;

-- 如果需要清理错误数据并重新插入正确的关联关系：
-- 1. 清空错误数据
-- DELETE FROM iam_account_role WHERE account_id NOT IN (SELECT id FROM iam_account) OR role_id NOT IN (SELECT id FROM iam_role);

-- 2. 重新插入正确的关联关系
-- INSERT INTO iam_account_role (id, tenant_id, account_id, role_id) VALUES
-- (1, 0, 1, 1);  -- admin 账户关联 super-admin 角色
