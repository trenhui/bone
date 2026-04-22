-- 为用户表添加首次登录强制修改密码标记
ALTER TABLE iam_user ADD COLUMN force_change_password TINYINT NOT NULL DEFAULT 1 COMMENT '首次登录强制修改密码标记: 1-需要修改, 0-已修改' AFTER status;

-- 更新现有管理员账号为需要强制修改密码
UPDATE iam_user SET force_change_password = 1 WHERE username = 'admin';
