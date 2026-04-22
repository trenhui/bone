-- Bone IAM 模块生成 biz_id 数据迁移脚本
-- 为现有数据生成 UUID 格式的业务 ID

-- 1. 为用户表生成 biz_id
UPDATE iam_user SET biz_id = REPLACE(UUID(), '-', '');

-- 2. 为角色表生成 biz_id
UPDATE iam_role SET biz_id = REPLACE(UUID(), '-', '');

-- 3. 为权限表生成 biz_id
UPDATE iam_permission SET biz_id = REPLACE(UUID(), '-', '');

-- 4. 更新用户角色关联表的业务ID
UPDATE iam_user_role ur
JOIN iam_user u ON ur.user_id = u.id
JOIN iam_role r ON ur.role_id = r.id
SET ur.user_biz_id = u.biz_id, ur.role_biz_id = r.biz_id;

-- 5. 更新角色权限关联表的业务ID
UPDATE iam_role_permission rp
JOIN iam_role r ON rp.role_id = r.id
JOIN iam_permission p ON rp.permission_id = p.id
SET rp.role_biz_id = r.biz_id, rp.permission_biz_id = p.biz_id;
