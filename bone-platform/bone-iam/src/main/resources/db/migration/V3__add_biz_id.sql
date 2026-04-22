-- Bone IAM 模块添加 biz_id 字段迁移脚本
-- 为双 ID 模型添加业务 ID 字段

-- 1. 用户表添加 biz_id 字段
ALTER TABLE iam_user ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE iam_user ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 2. 角色表添加 biz_id 字段
ALTER TABLE iam_role ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE iam_role ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 3. 权限表添加 biz_id 字段
ALTER TABLE iam_permission ADD COLUMN biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '业务ID（UUID）' AFTER id;
ALTER TABLE iam_permission ADD UNIQUE INDEX idx_biz_id (biz_id);

-- 4. 用户角色关联表修改为使用 biz_id
ALTER TABLE iam_user_role ADD COLUMN user_biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '用户业务ID' AFTER user_id;
ALTER TABLE iam_user_role ADD COLUMN role_biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '角色业务ID' AFTER role_id;
ALTER TABLE iam_user_role ADD INDEX idx_user_biz_id (user_biz_id);
ALTER TABLE iam_user_role ADD INDEX idx_role_biz_id (role_biz_id);

-- 5. 角色权限关联表修改为使用 biz_id
ALTER TABLE iam_role_permission ADD COLUMN role_biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '角色业务ID' AFTER role_id;
ALTER TABLE iam_role_permission ADD COLUMN permission_biz_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '权限业务ID' AFTER permission_id;
ALTER TABLE iam_role_permission ADD INDEX idx_role_biz_id (role_biz_id);
ALTER TABLE iam_role_permission ADD INDEX idx_permission_biz_id (permission_biz_id);

-- 6. 审计日志表修改为使用 biz_id
ALTER TABLE iam_audit_log ADD COLUMN target_biz_id VARCHAR(64) COMMENT '目标业务ID' AFTER target_id;
ALTER TABLE iam_audit_log ADD INDEX idx_target_biz_id (target_biz_id);
