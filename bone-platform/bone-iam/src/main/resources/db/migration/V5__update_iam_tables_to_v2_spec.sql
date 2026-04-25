-- ============================================================
-- IAM账号权限管理模块 - 升级到V2设计规范
-- ============================================================
-- 此脚本将IAM模块更新为设计文档V2规范：
-- - 表从iam_user重命名为iam_account以匹配领域术语
-- - 字段对齐设计文档规范
-- - 添加缺失的表和字段
-- - 添加索引优化

-- ============================================================
-- 1. 将iam_user迁移为iam_account
-- ============================================================

-- 创建新的iam_account表（符合设计规范）
CREATE TABLE IF NOT EXISTS iam_account_new (
    id                  BIGINT          NOT NULL COMMENT '账户主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '所属租户ID，0为平台',
    username            VARCHAR(100)    NOT NULL COMMENT '用户名',
    password_hash       VARCHAR(255)    NOT NULL COMMENT '密码哈希值',
    email               VARCHAR(200)    NOT NULL COMMENT '邮箱',
    phone               VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    real_name           VARCHAR(100)    DEFAULT NULL COMMENT '真实姓名',
    avatar_url          VARCHAR(500)    DEFAULT NULL COMMENT '头像URL',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '账户状态：0-禁用，1-启用，2-锁定',
    is_admin            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否管理员：0-否，1-是',
    last_login_at       DATETIME(3)     DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip       VARCHAR(50)     DEFAULT NULL COMMENT '最后登录IP',
    login_fail_count    SMALLINT        NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_until        DATETIME(3)     DEFAULT NULL COMMENT '锁定截止时间',
    pwd_updated_at      DATETIME(3)     DEFAULT NULL COMMENT '密码最后修改时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_account_username (tenant_id, username),
    KEY idx_iam_account_tenant (tenant_id),
    KEY idx_iam_account_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='账户表';

-- 迁移数据（如果旧表存在）
INSERT IGNORE INTO iam_account_new
    (id, tenant_id, username, password_hash, email, phone, real_name, avatar_url,
     status, is_admin, last_login_at, last_login_ip, login_fail_count,
     locked_until, pwd_updated_at, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    id, 0 as tenant_id, username, password_hash, email, phone, real_name, avatar,
    CASE status WHEN 'active' THEN 1 WHEN 'locked' THEN 2 ELSE 0 END as status,
    is_admin, last_login_time as last_login_at, last_login_ip, fail_count as login_fail_count,
    locked_until, password_changed_at as pwd_updated_at,
    created_by, updated_by, created_at, updated_at, deleted, version
FROM iam_user
WHERE NOT EXISTS (SELECT 1 FROM iam_account_new);

-- 重命名表
DROP TABLE IF EXISTS iam_user_old;
RENAME TABLE iam_user TO iam_user_old, iam_account_new TO iam_account;

-- ============================================================
-- 2. 更新iam_role表
-- ============================================================

-- 创建新的iam_role表
CREATE TABLE IF NOT EXISTS iam_role_new (
    id                  BIGINT          NOT NULL COMMENT '角色主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '所属租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '角色名称',
    code                VARCHAR(100)    NOT NULL COMMENT '角色编码',
    type                TINYINT         NOT NULL DEFAULT 1 COMMENT '角色类型：0-系统角色，1-自定义角色',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '角色描述',
    parent_role_id      BIGINT          DEFAULT NULL COMMENT '父角色ID（支持继承）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_code (tenant_id, code),
    KEY idx_iam_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='角色表';

-- 迁移角色数据
INSERT IGNORE INTO iam_role_new
    (id, tenant_id, name, code, type, description, created_by, updated_by, created_at, updated_at, deleted, version)
SELECT
    id, 0 as tenant_id, name, code,
    CASE is_system WHEN 1 THEN 0 ELSE 1 END as type,
    description, created_by, updated_by, created_at, updated_at, deleted, version
FROM iam_role
WHERE NOT EXISTS (SELECT 1 FROM iam_role_new);

DROP TABLE IF EXISTS iam_role_old;
RENAME TABLE iam_role TO iam_role_old, iam_role_new TO iam_role;

-- ============================================================
-- 3. 更新iam_permission表
-- ============================================================

CREATE TABLE IF NOT EXISTS iam_permission_new (
    id                  BIGINT          NOT NULL COMMENT '权限主键（Snowflake）',
    code                VARCHAR(200)    NOT NULL COMMENT '权限编码',
    name                VARCHAR(200)    NOT NULL COMMENT '权限名称',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_path       VARCHAR(500)    NOT NULL COMMENT '资源路径',
    action              VARCHAR(50)     NOT NULL COMMENT '操作：view,create,update,delete等',
    parent_id           BIGINT          DEFAULT NULL COMMENT '父权限ID',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_permission_code (code),
    KEY idx_iam_permission_resource (resource_type, resource_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='权限表';

-- 迁移权限数据
INSERT IGNORE INTO iam_permission_new
    (id, code, name, resource_type, resource_path, action, created_by, updated_by, created_at, updated_at, deleted)
SELECT
    id, permission_code as code, name, resource_type, resource_path, action,
    created_by, updated_by, created_at, updated_at, deleted
FROM iam_permission
WHERE NOT EXISTS (SELECT 1 FROM iam_permission_new);

DROP TABLE IF EXISTS iam_permission_old;
RENAME TABLE iam_permission TO iam_permission_old, iam_permission_new TO iam_permission;

-- ============================================================
-- 4. 更新账户-角色关联表
-- ============================================================

CREATE TABLE IF NOT EXISTS iam_account_role_new (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    account_id          BIGINT          NOT NULL COMMENT '账户ID',
    role_id             BIGINT          NOT NULL COMMENT '角色ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_account_role (account_id, role_id),
    KEY idx_iam_account_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='账户角色关联表';

-- 迁移数据
INSERT IGNORE INTO iam_account_role_new
    (id, tenant_id, account_id, role_id, created_by, updated_by, created_at, updated_at, deleted)
SELECT
    id, 0 as tenant_id, user_id as account_id, role_id,
    created_by, updated_by, created_at, updated_at, deleted
FROM iam_user_role
WHERE NOT EXISTS (SELECT 1 FROM iam_account_role_new);

DROP TABLE IF EXISTS iam_user_role_old;
RENAME TABLE iam_user_role TO iam_user_role_old, iam_account_role_new TO iam_account_role;

-- ============================================================
-- 5. 角色-权限关联表无需修改，更新索引
-- ============================================================

-- ============================================================
-- 6. 新增ABAC策略表
-- ============================================================

CREATE TABLE IF NOT EXISTS iam_policy (
    id                  BIGINT          NOT NULL COMMENT '策略主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    code                VARCHAR(100)    NOT NULL COMMENT '策略编码',
    name                VARCHAR(200)    NOT NULL COMMENT '策略名称',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_path       VARCHAR(500)    NOT NULL COMMENT '资源路径',
    action              VARCHAR(50)     NOT NULL COMMENT '操作',
    `condition`         JSON            NOT NULL COMMENT 'SpEL条件表达式',
    effect              VARCHAR(10)     NOT NULL DEFAULT 'ALLOW' COMMENT '效果：ALLOW-允许，DENY-拒绝',
    priority            INT             NOT NULL DEFAULT 0 COMMENT '优先级',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_policy_code (code),
    KEY idx_iam_policy_resource (resource_type, resource_path, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='ABAC策略表';

-- ============================================================
-- 7. 更新审计日志表（分区表）
-- ============================================================

-- 先创建非分区版本，生产环境可手动转换为分区表
CREATE TABLE IF NOT EXISTS iam_audit_log_new (
    id                  BIGINT          NOT NULL COMMENT '审计主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '操作用户ID',
    username            VARCHAR(100)    NOT NULL COMMENT '用户名',
    action              VARCHAR(100)    NOT NULL COMMENT '操作动作',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_id         VARCHAR(200)    DEFAULT NULL COMMENT '资源ID',
    request_method      VARCHAR(10)     DEFAULT NULL COMMENT '请求方法',
    request_url         VARCHAR(500)    DEFAULT NULL COMMENT '请求URL',
    request_params      TEXT            DEFAULT NULL COMMENT '请求参数',
    request_body        MEDIUMTEXT      DEFAULT NULL COMMENT '请求体',
    response_status     INT             DEFAULT NULL COMMENT '响应状态码',
    result              VARCHAR(20)     NOT NULL COMMENT '操作结果：SUCCESS/FAILED',
    error_message       TEXT            DEFAULT NULL COMMENT '错误信息',
    source_ip           VARCHAR(50)     DEFAULT NULL COMMENT '来源IP',
    user_agent          VARCHAR(500)    DEFAULT NULL COMMENT '用户代理',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id, created_at),
    KEY idx_iam_audit_tenant (tenant_id),
    KEY idx_iam_audit_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审计日志表';

-- 迁移审计日志数据
INSERT IGNORE INTO iam_audit_log_new
    (id, tenant_id, user_id, username, action, resource_type, resource_id,
     request_method, request_url, request_params, request_body, response_status,
     result, error_message, source_ip, user_agent, duration_ms, created_at)
SELECT
    id, 0 as tenant_id, user_id, username, action,
    COALESCE(resource_type, 'unknown') as resource_type, resource_id,
    request_method, request_url, request_params, request_body, response_status,
    result, error_message, ip_address as source_ip, user_agent,
    TIMESTAMPDIFF(MICROSECOND, created_at, created_at) / 1000 as duration_ms,
    created_at
FROM iam_audit_log
WHERE NOT EXISTS (SELECT 1 FROM iam_audit_log_new WHERE iam_audit_log_new.id = iam_audit_log.id);

DROP TABLE IF EXISTS iam_audit_log_old;
RENAME TABLE iam_audit_log TO iam_audit_log_old, iam_audit_log_new TO iam_audit_log;

-- ============================================================
-- 8. 新增刷新令牌表
-- ============================================================

CREATE TABLE IF NOT EXISTS iam_refresh_token (
    id                  BIGINT          NOT NULL COMMENT '令牌主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    account_id          BIGINT          NOT NULL COMMENT '账户ID',
    token_hash          VARCHAR(255)    NOT NULL COMMENT '令牌哈希值',
    expires_at          DATETIME(3)     NOT NULL COMMENT '过期时间',
    is_revoked          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已撤销：0-否，1-是',
    replaced_by         VARCHAR(255)    DEFAULT NULL COMMENT '替换令牌哈希',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_refresh_token_hash (token_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='刷新令牌表';

-- ============================================================
-- 9. 新增租户表
-- ============================================================

CREATE TABLE IF NOT EXISTS iam_tenant (
    id                  BIGINT          NOT NULL COMMENT '租户主键（Snowflake）',
    name                VARCHAR(200)    NOT NULL COMMENT '租户名称',
    code                VARCHAR(50)     NOT NULL COMMENT '租户编码',
    level               TINYINT         NOT NULL DEFAULT 0 COMMENT '租户等级：0-标准，1-VIP，2-企业',
    isolation_mode      VARCHAR(20)     NOT NULL DEFAULT 'ROW_LEVEL' COMMENT '数据隔离模式：ROW_LEVEL-行级，SCHEMA_LEVEL-模式级，DATABASE_LEVEL-库级',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待开通，1-已激活，2-已暂停，3-已注销',
    admin_email         VARCHAR(200)    NOT NULL COMMENT '管理员邮箱',
    contact_name        VARCHAR(100)    DEFAULT NULL COMMENT '联系人姓名',
    contact_phone       VARCHAR(20)     DEFAULT NULL COMMENT '联系人电话',
    schema_name         VARCHAR(64)     DEFAULT NULL COMMENT '独立Schema名称（Schema隔离时使用）',
    db_instance         VARCHAR(200)    DEFAULT NULL COMMENT '独立数据库实例（库隔离时使用）',
    activated_at        DATETIME(3)     DEFAULT NULL COMMENT '激活时间',
    expired_at          DATETIME(3)     DEFAULT NULL COMMENT '过期时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_tenant_code (code),
    KEY idx_iam_tenant_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='租户表';

-- ============================================================
-- 10. 插入默认数据
-- ============================================================

-- 插入默认超级管理员账户 (密码: admin123, 实际使用时请通过BCrypt加密)
INSERT IGNORE INTO iam_account (id, tenant_id, username, password_hash, email, real_name, status, is_admin)
VALUES (1, 0, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@bone.com', '系统管理员', 1, 1);

-- 插入默认角色
INSERT IGNORE INTO iam_role (id, tenant_id, name, code, type, description)
VALUES
(1, 0, '超级管理员', 'SUPER_ADMIN', 0, '拥有所有权限的系统超级管理员'),
(2, 0, '普通用户', 'USER', 0, '普通用户角色');

-- 为admin分配超级管理员角色
INSERT IGNORE INTO iam_account_role (id, tenant_id, account_id, role_id)
VALUES (1, 0, 1, 1);

-- 插入默认权限
INSERT IGNORE INTO iam_permission (id, code, name, resource_type, resource_path, action)
VALUES
(1, 'account:view', '查看用户', 'account', '/api/iam/accounts', 'view'),
(2, 'account:create', '创建用户', 'account', '/api/iam/accounts', 'create'),
(3, 'account:update', '更新用户', 'account', '/api/iam/accounts', 'update'),
(4, 'account:delete', '删除用户', 'account', '/api/iam/accounts', 'delete'),
(5, 'role:view', '查看角色', 'role', '/api/iam/roles', 'view'),
(6, 'role:create', '创建角色', 'role', '/api/iam/roles', 'create'),
(7, 'role:update', '更新角色', 'role', '/api/iam/roles', 'update'),
(8, 'role:delete', '删除角色', 'role', '/api/iam/roles', 'delete'),
(9, 'permission:view', '查看权限', 'permission', '/api/iam/permissions', 'view'),
(10, 'audit:view', '查看审计日志', 'audit', '/api/iam/audit', 'view');

-- 为超级管理员分配所有权限
INSERT IGNORE INTO iam_role_permission (id, role_id, permission_id)
SELECT p.id as id, 1 as role_id, p.id as permission_id FROM iam_permission p;
