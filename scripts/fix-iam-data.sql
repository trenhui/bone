-- 修复 iam_account_role 表中的错误数据
-- 当前 role_id 列包含字符串值 "role-super-admin"，需要改为对应的数字 ID

-- 首先，查看当前数据
SELECT * FROM iam_account_role;
SELECT * FROM iam_role;

-- 查看 iam_role 表中 SUPER_ADMIN 角色的实际 id
-- 根据 bone-init.sql，SUPER_ADMIN 角色的 id 应该是 1

-- 修复 iam_account_role 表
UPDATE iam_account_role
SET role_id = 1
WHERE role_id = 'role-super-admin' OR role_id = 'SUPER_ADMIN';

-- 如果上面的方法不起作用，可能需要先获取 SUPER_ADMIN 的正确 id
-- 然后再更新
-- 假设 SUPER_ADMIN 角色的 code 是 'SUPER_ADMIN'
-- UPDATE iam_account_role ar
-- SET ar.role_id = r.id
-- FROM iam_account_role ar
-- INNER JOIN iam_role r ON r.code = 'SUPER_ADMIN'
-- WHERE ar.role_id = 'role-super-admin';

-- 验证修复结果
SELECT * FROM iam_account_role;
