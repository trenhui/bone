-- ============================================================
-- Bone 数据库初始化脚本（最终版）
-- 规范: doc/architecture/数据库开发规范.md
-- 使用: mysql -u root -p < bone-init.sql
-- 说明: 内部开发库可 DROP DATABASE 后全量重建，无增量迁移
-- ============================================================

CREATE DATABASE IF NOT EXISTS bone
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE bone;

-- 确保连接字符集正确，防止中文乱码
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;
SET character_set_connection=utf8mb4;

-- ============================================================
-- 1. IAM
-- ============================================================

DROP TABLE IF EXISTS iam_refresh_token;
DROP TABLE IF EXISTS iam_policy;
DROP TABLE IF EXISTS iam_account_role;
DROP TABLE IF EXISTS iam_role_permission;
DROP TABLE IF EXISTS iam_audit_settings;
DROP TABLE IF EXISTS iam_audit_log;
DROP TABLE IF EXISTS iam_account;
DROP TABLE IF EXISTS iam_role;
DROP TABLE IF EXISTS iam_permission;
DROP TABLE IF EXISTS iam_tenant;

CREATE TABLE iam_tenant (
    id                  BIGINT          NOT NULL COMMENT '租户主键（Snowflake）',
    name                VARCHAR(200)    NOT NULL COMMENT '租户名称',
    code                VARCHAR(50)     NOT NULL COMMENT '租户编码',
    level               TINYINT         NOT NULL DEFAULT 0 COMMENT '租户等级',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态',
    admin_email         VARCHAR(200)    NOT NULL COMMENT '管理员邮箱',
    max_accounts        INT             DEFAULT NULL COMMENT '账号配额上限，NULL=不限制',
    max_roles           INT             DEFAULT NULL COMMENT '角色配额上限，NULL=不限制',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_tenant_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表';

CREATE TABLE iam_account (
    id                  BIGINT          NOT NULL COMMENT '账户主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    username            VARCHAR(100)    NOT NULL COMMENT '用户名',
    password_hash       VARCHAR(255)    NOT NULL COMMENT '密码哈希（BCrypt）',
    email               VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '邮箱',
    phone               VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
    real_name           VARCHAR(100)    DEFAULT NULL COMMENT '真实姓名',
    avatar_url          VARCHAR(500)    DEFAULT NULL COMMENT '头像URL',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '0-禁用 1-启用 2-锁定',
    is_admin            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否管理员',
    last_login_at       DATETIME(3)     DEFAULT NULL COMMENT '最后登录时间',
    last_login_ip       VARCHAR(50)     DEFAULT NULL COMMENT '最后登录IP',
    login_fail_count    SMALLINT        NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    locked_at        DATETIME(3)     DEFAULT NULL COMMENT '锁定截止时间',
    password_updated_at      DATETIME(3)     DEFAULT NULL COMMENT '密码最后修改时间',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_account_username (tenant_id, username),
    KEY idx_iam_account_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM账户';

CREATE TABLE iam_role (
    id                  BIGINT          NOT NULL COMMENT '角色主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '角色名称',
    code                VARCHAR(100)    NOT NULL COMMENT '角色编码',
    type                TINYINT         NOT NULL DEFAULT 1 COMMENT '0-系统 1-自定义',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '角色描述',
    parent_role_id      BIGINT          DEFAULT NULL COMMENT '父角色ID',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_code (tenant_id, code),
    KEY idx_iam_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM角色';

CREATE TABLE iam_permission (
    id                  BIGINT          NOT NULL COMMENT '权限主键（Snowflake）',
    code                VARCHAR(200)    NOT NULL COMMENT '权限编码',
    name                VARCHAR(200)    NOT NULL COMMENT '权限名称',
    resource_type       VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '资源类型',
    resource_path       VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '资源路径',
    action              VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '操作',
    parent_id           BIGINT          DEFAULT NULL COMMENT '父权限ID',
    type                VARCHAR(50)     NOT NULL DEFAULT 'OPERATION' COMMENT '权限类型',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_permission_code (code),
    KEY idx_iam_permission_resource (resource_type, resource_path(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM权限';

CREATE TABLE iam_account_role (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    account_id          BIGINT          NOT NULL COMMENT '账户ID',
    role_id             BIGINT          NOT NULL COMMENT '角色ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_account_role (account_id, role_id),
    KEY idx_iam_account_role_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账户角色关联';

CREATE TABLE iam_role_permission (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    role_id             BIGINT          NOT NULL COMMENT '角色ID',
    permission_id       BIGINT          NOT NULL COMMENT '权限ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联';

CREATE TABLE iam_audit_log (
    id                  BIGINT          NOT NULL COMMENT '审计主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id             BIGINT          DEFAULT NULL COMMENT '操作用户ID',
    operation           VARCHAR(50)     NOT NULL COMMENT '操作类型',
    resource_id         VARCHAR(100)    DEFAULT NULL COMMENT '资源ID',
    resource_type       VARCHAR(50)     DEFAULT NULL COMMENT '资源类型',
    ip                  VARCHAR(45)     DEFAULT NULL COMMENT '操作IP',
    user_agent          VARCHAR(255)    DEFAULT NULL COMMENT '用户代理',
    parameters          TEXT            DEFAULT NULL COMMENT '请求参数',
    result              TEXT            DEFAULT NULL COMMENT '操作结果',
    duration            INT             DEFAULT NULL COMMENT '耗时毫秒',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_iam_audit_user_time (user_id, created_at),
    KEY idx_iam_audit_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM审计日志';

CREATE TABLE iam_audit_settings (
    id                      BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id               BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    retention_days          INT             NOT NULL DEFAULT 30 COMMENT '日志保留天数',
    auto_archive_enabled    TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否自动归档',
    archive_after_days      INT             NOT NULL DEFAULT 15 COMMENT '归档阈值天数',
    storage_type            VARCHAR(32)     NOT NULL DEFAULT 'DATABASE' COMMENT '存储类型',
    worm_enabled            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'WORM 合规',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    version                 INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_audit_settings_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM 审计策略配置';

CREATE TABLE iam_policy (
    id                  BIGINT          NOT NULL COMMENT '策略主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    code                VARCHAR(100)    NOT NULL COMMENT '策略编码',
    name                VARCHAR(200)    NOT NULL COMMENT '策略名称',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_path       VARCHAR(500)    NOT NULL COMMENT '资源路径',
    action              VARCHAR(50)     NOT NULL COMMENT '操作',
    `condition`         JSON            NOT NULL COMMENT 'SpEL条件',
    effect              VARCHAR(10)     NOT NULL DEFAULT 'ALLOW' COMMENT 'ALLOW/DENY',
    priority            INT             NOT NULL DEFAULT 0 COMMENT '优先级',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_policy_code (tenant_id, code),
    KEY idx_iam_policy_resource (resource_type, resource_path(100), action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ABAC策略表';

CREATE TABLE iam_refresh_token (
    id                  BIGINT          NOT NULL COMMENT '令牌主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    account_id          BIGINT          NOT NULL COMMENT '账户ID',
    token_hash          VARCHAR(255)    NOT NULL COMMENT '令牌哈希',
    expires_at          DATETIME(3)     NOT NULL COMMENT '过期时间',
    is_revoked          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否撤销',
    replaced_by         VARCHAR(255)    DEFAULT NULL COMMENT '替换令牌哈希',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_refresh_token_hash (token_hash),
    KEY idx_iam_refresh_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新令牌表';

CREATE TABLE iam_dept (
    id                  BIGINT          NOT NULL COMMENT '部门主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '部门名称',
    parent_id           BIGINT          DEFAULT NULL COMMENT '父部门ID',
    order_no            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态 0-停用 1-启用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_iam_dept_tenant (tenant_id),
    KEY idx_iam_dept_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM部门';

CREATE TABLE iam_menu (
    id                  BIGINT          NOT NULL COMMENT '菜单主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '菜单名称',
    parent_id           BIGINT          DEFAULT NULL COMMENT '父菜单ID',
    path                VARCHAR(500)    DEFAULT NULL COMMENT '路由路径',
    icon                VARCHAR(200)    DEFAULT NULL COMMENT '图标',
    order_no            INT             NOT NULL DEFAULT 0 COMMENT '排序',
    permission          VARCHAR(200)    DEFAULT NULL COMMENT '权限标识',
    type                TINYINT         NOT NULL DEFAULT 1 COMMENT '类型 0-目录 1-菜单 2-按钮',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_iam_menu_tenant (tenant_id),
    KEY idx_iam_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IAM菜单';

-- BONE_IAM_DEMO_PASSWORD_ACK: 演示账号 admin 默认口令为 123456（仅开发/CI 允许；生产须改密）
INSERT INTO iam_account (id, tenant_id, username, password_hash, email, real_name, status, is_admin)
VALUES (1, 0, 'admin', '$2a$10$nZLjv4A8i.Q64tYZxrXVTuPQJ.g337OkdOx8rAKnKJL3a2dqdKR8q', 'admin@bone.com', '系统管理员', 1, 1);

INSERT INTO iam_role (id, tenant_id, name, code, type, description)
VALUES
    (1, 0, '超级管理员', 'SUPER_ADMIN', 0, '系统超级管理员'),
    (2, 0, '普通用户', 'USER', 1, '普通用户');

INSERT INTO iam_account_role (id, tenant_id, account_id, role_id)
VALUES (1, 0, 1, 1);

INSERT INTO iam_permission (id, code, name, resource_type, resource_path, action, type, sort_order, description)
VALUES
    (1, 'iam:accounts:read', 'IAM-账号查看', 'iam', 'accounts', 'read', 'OPERATION', 10, '平台权限目录'),
    (2, 'iam:accounts:write', 'IAM-账号维护', 'iam', 'accounts', 'write', 'OPERATION', 20, NULL),
    (3, 'iam:roles:read', 'IAM-角色查看', 'iam', 'roles', 'read', 'OPERATION', 30, NULL),
    (4, 'iam:roles:write', 'IAM-角色维护', 'iam', 'roles', 'write', 'OPERATION', 40, NULL),
    (5, 'iam:permissions:read', 'IAM-权限查看', 'iam', 'permissions', 'read', 'OPERATION', 50, NULL),
    (6, 'iam:permissions:write', 'IAM-权限维护', 'iam', 'permissions', 'write', 'OPERATION', 60, NULL),
    (7, 'metadata:read', '元数据-读', 'metadata', '*', 'read', 'OPERATION', 70, NULL),
    (8, 'metadata:write', '元数据-写', 'metadata', '*', 'write', 'OPERATION', 80, NULL),
    (19, 'metadata:publish', '元数据-发布', 'metadata', '*', 'publish', 'OPERATION', 85, 'catalog 实体 publish'),
    (9, 'extension:points:read', '扩展点-读', 'extension', 'points', 'read', 'OPERATION', 90, NULL),
    (10, 'extension:points:write', '扩展点-写', 'extension', 'points', 'write', 'OPERATION', 100, NULL),
    (11, 'extension:plugins:deploy', '插件-部署', 'extension', 'plugins', 'deploy', 'OPERATION', 110, NULL),
    (12, 'iam:audit:read', 'IAM-审计查看', 'iam', 'audit', 'read', 'OPERATION', 120, NULL),
    (13, 'iam:audit:write', 'IAM-审计设置', 'iam', 'audit', 'write', 'OPERATION', 130, NULL),
    (14, 'iam:tenants:read', 'IAM-租户查看', 'iam', 'tenants', 'read', 'OPERATION', 140, NULL),
    (15, 'iam:tenants:write', 'IAM-租户维护', 'iam', 'tenants', 'write', 'OPERATION', 150, NULL),
    (16, 'iam:sessions:read', 'IAM-会话查看', 'iam', 'sessions', 'read', 'OPERATION', 160, '查看 refresh token 在线会话'),
    (17, 'iam:sessions:write', 'IAM-会话吊销', 'iam', 'sessions', 'write', 'OPERATION', 170, '强制下线/吊销 refresh token'),
    (18, 'sys:console:read', 'SYS-控制台查看', 'system', 'console', 'read', 'OPERATION', 180, '查看平台概览/服务状态/关键指标/快捷操作'),
    (20, 'order:orders:read', '订单-查看', 'order', 'orders', 'read', 'OPERATION', 190, 'bone-blueprint 订单查询（样板 scope 示范）'),
    (21, 'order:orders:write', '订单-维护', 'order', 'orders', 'write', 'OPERATION', 200, 'bone-blueprint 下单/取消/发货/送达');

INSERT INTO iam_role_permission (id, role_id, permission_id)
VALUES
    (1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5), (6, 1, 6),
    (7, 1, 7), (8, 1, 8), (9, 1, 9), (10, 1, 10), (11, 1, 11),
    (12, 1, 12), (13, 1, 13), (14, 1, 14), (15, 1, 15),
    (16, 1, 16), (17, 1, 17), (18, 1, 18), (19, 1, 19),
    (20, 1, 20), (21, 1, 21);

-- ============================================================
-- 2. System
-- ============================================================

DROP TABLE IF EXISTS sys_alert_event;
DROP TABLE IF EXISTS sys_alert_rule;
DROP TABLE IF EXISTS sys_log;
DROP TABLE IF EXISTS sys_dict;
DROP TABLE IF EXISTS sys_schedule_task;
DROP TABLE IF EXISTS sys_config;

CREATE TABLE sys_config (
    id                  BIGINT          NOT NULL COMMENT '配置主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    config_key          VARCHAR(100)    NOT NULL COMMENT '配置键',
    config_value        TEXT            NOT NULL COMMENT '配置值',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '配置描述',
    config_type         VARCHAR(20)     NOT NULL COMMENT 'SYSTEM/SERVICE/FEATURE',
    encrypted           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否加密存储',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (tenant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';

CREATE TABLE sys_log (
    id                  BIGINT          NOT NULL COMMENT '日志主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    level               VARCHAR(20)     NOT NULL COMMENT 'ERROR/WARN/INFO/DEBUG/TRACE',
    service             VARCHAR(100)    NOT NULL COMMENT '服务名称',
    content             TEXT            NOT NULL COMMENT '日志内容',
    trace_id            VARCHAR(100)    DEFAULT NULL COMMENT '追踪ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_log_level (level),
    KEY idx_sys_log_service (service),
    KEY idx_sys_log_trace (trace_id),
    KEY idx_sys_log_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志';

CREATE TABLE sys_dict (
    id                  BIGINT          NOT NULL COMMENT '字典主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    type                VARCHAR(50)     NOT NULL COMMENT '字典类型（DictType 枚举名）',
    type_name           VARCHAR(100)    DEFAULT NULL COMMENT '类型名称',
    code                VARCHAR(100)    DEFAULT NULL COMMENT '字典编码',
    label               VARCHAR(100)    DEFAULT NULL COMMENT '字典显示名',
    value               VARCHAR(255)    DEFAULT NULL COMMENT '字典值',
    sort                INT             DEFAULT 0 COMMENT '排序',
    status              INT             NOT NULL DEFAULT 1 COMMENT '状态（1=启用 0=禁用）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_sys_dict_type (type),
    KEY idx_sys_dict_code (code),
    KEY idx_sys_dict_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统字典';

CREATE TABLE sys_schedule_task (
    id                  BIGINT          NOT NULL COMMENT '任务主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '任务名称',
    cron                VARCHAR(100)    DEFAULT NULL COMMENT 'Cron 表达式',
    handler             VARCHAR(255)    DEFAULT NULL COMMENT '任务处理器',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DISABLED' COMMENT '任务状态（TaskStatus 枚举名）',
    last_run_at         DATETIME(3)     DEFAULT NULL COMMENT '上次执行时间',
    next_run_at         DATETIME(3)     DEFAULT NULL COMMENT '下次执行时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_sys_schedule_status (status),
    KEY idx_sys_schedule_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统定时任务';

CREATE TABLE sys_alert_rule (
    id                  BIGINT          NOT NULL COMMENT '规则主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '规则名称',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '规则描述',
    metric_name         VARCHAR(100)    NOT NULL COMMENT '指标名称',
    threshold_value     DECIMAL(20,4)   NOT NULL COMMENT '阈值',
    alert_level         VARCHAR(20)     NOT NULL COMMENT 'CRITICAL/WARNING/INFO',
    notification_channels JSON          DEFAULT NULL COMMENT '通知渠道',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_sys_alert_rule_metric (metric_name),
    KEY idx_sys_alert_rule_level (alert_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警规则';

CREATE TABLE sys_alert_event (
    id                  BIGINT          NOT NULL COMMENT '事件主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    rule_id             BIGINT          NOT NULL COMMENT '告警规则ID',
    rule_name           VARCHAR(100)    DEFAULT NULL COMMENT '规则名称',
    alert_level         VARCHAR(20)     NOT NULL COMMENT '告警级别',
    metric_name         VARCHAR(100)    NOT NULL COMMENT '指标名称',
    current_value       DECIMAL(20,4)   NOT NULL COMMENT '当前值',
    threshold_value     DECIMAL(20,4)   NOT NULL COMMENT '阈值',
    message             VARCHAR(500)    NOT NULL COMMENT '告警消息',
    status              VARCHAR(20)     NOT NULL DEFAULT 'TRIGGERED' COMMENT 'TRIGGERED/RESOLVED',
    resolved_at         DATETIME(3)     DEFAULT NULL COMMENT '恢复时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_alert_event_rule (rule_id),
    KEY idx_sys_alert_event_status (status),
    KEY idx_sys_alert_event_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件';

-- ------------------------------------------------------------
-- 2.1 System 演示种子数据（MVP-09：配置管理 / 监控告警页面开箱即有内容）
--
-- 背景：此前 sys_config / sys_alert_rule 仅有表结构、零数据，前端「系统 / 配置管理」
-- 与「系统 / 监控告警」打开即为空列表，详情页（/system/config/{id}、/system/alert/rules/{id}）
-- 取不到真实 id，MVP 验收无法演示。此处补齐最小演示集，按唯一键幂等。
-- ------------------------------------------------------------
INSERT INTO sys_config (id, tenant_id, config_key, config_value, description, config_type, encrypted)
VALUES
    (1, 0, 'platform.name', 'Bone Platform', '平台显示名称', 'SYSTEM', 0),
    (2, 0, 'feature.runtime.crud', 'true', '模式B运行时动态 CRUD 总开关', 'FEATURE', 0),
    (3, 0, 'feature.metadata.eav', 'false', 'EAV 扩展字段实验特性（MVP 默认关闭）', 'FEATURE', 0),
    (4, 0, 'service.metadata.timeout-ms', '3000', '元数据服务调用超时（毫秒）', 'SERVICE', 0)
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), description = VALUES(description);

INSERT INTO sys_alert_rule (id, tenant_id, name, description, metric_name, threshold_value, alert_level, notification_channels, enabled)
VALUES
    (1, 0, 'CPU 使用率过高', 'CPU 使用率持续高于阈值时触发', 'cpu.usage', 85.0000, 'CRITICAL', '["CONSOLE"]', 1),
    (2, 0, 'API 错误率过高', '网关 API 错误率高于阈值时触发', 'api.error_rate', 5.0000, 'WARNING', '["CONSOLE"]', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), threshold_value = VALUES(threshold_value);

-- ============================================================
-- 3. Integration（对齐 bone-platform/bone-integration 领域模型）
-- ============================================================

DROP TABLE IF EXISTS int_flow_connection;
DROP TABLE IF EXISTS int_flow_node;
DROP TABLE IF EXISTS int_execution_log;
DROP TABLE IF EXISTS int_dead_letter;
DROP TABLE IF EXISTS int_template;
DROP TABLE IF EXISTS int_flow;
DROP TABLE IF EXISTS int_outbox;
DROP TABLE IF EXISTS int_connector;

CREATE TABLE int_connector (
    id                  BIGINT          NOT NULL COMMENT '连接器主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '连接器名称',
    type                VARCHAR(50)     NOT NULL COMMENT '连接器类型',
    config              JSON            NOT NULL COMMENT '连接器配置',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DISABLED' COMMENT 'ENABLED/DISABLED',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_int_connector_tenant (tenant_id),
    KEY idx_int_connector_type (type),
    KEY idx_int_connector_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='连接器';

CREATE TABLE int_flow (
    id                  BIGINT          NOT NULL COMMENT '流程主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '流程名称',
    description         VARCHAR(255)    DEFAULT NULL COMMENT '流程描述',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ACTIVE/INACTIVE/DELETED',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    KEY idx_int_flow_tenant (tenant_id),
    KEY idx_int_flow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成流程';

CREATE TABLE int_flow_node (
    id                  BIGINT          NOT NULL COMMENT '节点主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    flow_id             BIGINT          NOT NULL COMMENT '流程ID',
    name                VARCHAR(100)    NOT NULL COMMENT '节点名称',
    type                VARCHAR(50)     NOT NULL COMMENT '节点类型',
    config              JSON            NOT NULL COMMENT '节点配置',
    position_x          INT             NOT NULL DEFAULT 0 COMMENT '画布X',
    position_y          INT             NOT NULL DEFAULT 0 COMMENT '画布Y',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_int_flow_node_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程节点';

CREATE TABLE int_flow_connection (
    id                  BIGINT          NOT NULL COMMENT '连线主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    flow_id             BIGINT          NOT NULL COMMENT '流程ID',
    source_node_id      BIGINT          NOT NULL COMMENT '源节点ID',
    target_node_id      BIGINT          NOT NULL COMMENT '目标节点ID',
    `condition`         VARCHAR(500)    DEFAULT NULL COMMENT '条件表达式',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_int_flow_conn_flow (flow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程连线';

CREATE TABLE int_execution_log (
    id                  BIGINT          NOT NULL COMMENT '执行记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    flow_id             BIGINT          NOT NULL COMMENT '流程ID',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
    started_at          DATETIME(3)     DEFAULT NULL COMMENT '开始时间',
    ended_at            DATETIME(3)     DEFAULT NULL COMMENT '结束时间',
    input_data          TEXT            DEFAULT NULL COMMENT '输入数据',
    output_data         TEXT            DEFAULT NULL COMMENT '输出数据',
    error_message       TEXT            DEFAULT NULL COMMENT '错误信息',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_int_execution_log_flow (flow_id),
    KEY idx_int_execution_log_status (status),
    KEY idx_int_execution_log_time (started_at, ended_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程执行日志';

CREATE TABLE int_dead_letter (
    id                  BIGINT          NOT NULL COMMENT '死信主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    flow_id             BIGINT          NOT NULL COMMENT '流程ID',
    original_message    JSON            DEFAULT NULL COMMENT '原始消息',
    headers             JSON            DEFAULT NULL COMMENT '消息头',
    error_message       TEXT            DEFAULT NULL COMMENT '错误消息',
    error_stack         TEXT            DEFAULT NULL COMMENT '错误堆栈',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RETRYING/RESOLVED/FAILED',
    max_retries         INT             NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    next_retry_at       DATETIME(3)     DEFAULT NULL COMMENT '下次重试时间',
    last_error          TEXT            DEFAULT NULL COMMENT '最后错误',
    resolved_at         DATETIME(3)     DEFAULT NULL COMMENT '解决时间',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_int_dl_flow (flow_id),
    KEY idx_int_dl_next_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='死信队列表';

CREATE TABLE int_outbox (
    id                  BIGINT          NOT NULL COMMENT 'Outbox 主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    event_id            VARCHAR(36)     NOT NULL COMMENT '信封 eventId（UUID）',
    event_type          VARCHAR(80)     NOT NULL COMMENT '事件类型',
    topic               VARCHAR(200)    NOT NULL COMMENT 'MQ Topic',
    partition_key       VARCHAR(100)    NOT NULL COMMENT '分区键（默认 tenant_id）',
    envelope_json       JSON            NOT NULL COMMENT '消息信封 JSON',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '中继重试次数',
    sent_at             DATETIME(3)     DEFAULT NULL COMMENT '发送成功时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_outbox_event_id (event_id),
    KEY idx_int_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成领域事件 Outbox';

-- ============================================================
-- 蓝图样板（bone-blueprint）：订单 / 支付限界上下文
-- ============================================================

CREATE TABLE t_order (
    id                  BIGINT          NOT NULL COMMENT '订单主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    customer_id         BIGINT          NOT NULL COMMENT '客户ID',
    total_amount        DECIMAL(18,2)   NOT NULL COMMENT '订单总额',
    status              VARCHAR(20)     NOT NULL COMMENT 'CREATED/PAID/SHIPPED/DELIVERED/CANCELLED/REFUNDED',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_order_tenant_customer (tenant_id, customer_id),
    KEY idx_order_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单（Order 上下文聚合根）';

-- 明细为 Order 聚合内实体：租户隔离经父聚合 t_order.tenant_id 间接保证，
-- 故不落 tenant_id 列（子表例外，见 数据库开发规范 §2 说明）。
CREATE TABLE t_order_item (
    id                  BIGINT          NOT NULL COMMENT '明细主键（Snowflake）',
    order_id            BIGINT          NOT NULL COMMENT '关联 t_order.id',
    product_id          BIGINT          NOT NULL COMMENT '商品ID',
    product_name        VARCHAR(200)    NOT NULL COMMENT '商品名称',
    quantity            INT             NOT NULL COMMENT '数量',
    unit_price          DECIMAL(18,2)   NOT NULL COMMENT '单价',
    subtotal            DECIMAL(18,2)   NOT NULL COMMENT '小计',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_order_item_order (order_id),
    KEY idx_order_item_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细（Order 聚合内实体）';

CREATE TABLE bp_payment (
    id                  BIGINT          NOT NULL COMMENT '支付单主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    order_id            BIGINT          NOT NULL COMMENT '关联 t_order.id',
    customer_id         BIGINT          NOT NULL COMMENT '客户ID',
    amount              DECIMAL(18,2)   NOT NULL COMMENT '支付金额',
    channel             VARCHAR(30)     NOT NULL COMMENT 'SIMULATED/WECHAT/ALIPAY',
    status              VARCHAR(20)     NOT NULL COMMENT 'PENDING/PAYING/SUCCESS/FAILED/CLOSED',
    channel_trade_no    VARCHAR(64)     DEFAULT NULL COMMENT '渠道流水号（消费端幂等键）',
    pay_url             VARCHAR(500)    DEFAULT NULL COMMENT '支付链接',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    paid_at             DATETIME(3)     DEFAULT NULL COMMENT '支付成功时间',
    refunded_at         DATETIME(3)     DEFAULT NULL COMMENT '退款时间',
    refund_amount       DECIMAL(18,2)   DEFAULT NULL COMMENT '退款金额',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_payment_tenant_order (tenant_id, order_id),
    KEY idx_payment_tenant_status (tenant_id, status),
    -- 幂等兜底：渠道回填的 channel_trade_no 回填后须唯一；MySQL 中 NULL 不计入唯一约束，
    -- 故多个未回调（NULL）行不冲突，回调回填后强制唯一，防渠道重复推送造成并发双写。
    UNIQUE KEY uk_payment_tenant_channel_trade_no (tenant_id, channel_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单（Payment 上下文聚合根）';

CREATE TABLE bp_outbox (
    id                  BIGINT          NOT NULL COMMENT 'Outbox 主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    event_id            VARCHAR(36)     NOT NULL COMMENT '信封 eventId（UUID）',
    event_type          VARCHAR(80)     NOT NULL COMMENT '事件类型',
    topic               VARCHAR(200)    NOT NULL COMMENT 'MQ Topic',
    partition_key       VARCHAR(100)    NOT NULL COMMENT '分区键（默认 tenant_id）',
    envelope_json       JSON            NOT NULL COMMENT '消息信封 JSON',
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0' COMMENT '信封 schema 版本',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '中继重试次数',
    sent_at             DATETIME(3)     DEFAULT NULL COMMENT '发送成功时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bp_outbox_event_id (event_id),
    KEY idx_bp_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='蓝图集成事件 Outbox';

-- 消费端幂等去重表（消息与事件规范 §5）：Outbox 为至少一次投递，消费端必须按 eventId 落库去重。
-- 保留期须 ≥ 最长重试窗口，可按 created_at 定期归档（见 idx_processed_event_created_at）。
CREATE TABLE bp_processed_event (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    consumer_group      VARCHAR(120)    NOT NULL COMMENT '消费组：同一 eventId 可被不同组各消费一次',
    event_id            VARCHAR(36)     NOT NULL COMMENT '信封 eventId（幂等键）',
    topic               VARCHAR(200)    NOT NULL COMMENT '来源 Topic',
    processed_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '处理完成时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_processed_event_consumer_event (consumer_group, event_id),
    KEY idx_processed_event_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消费端幂等去重（eventId）';

-- 幂等写快照（API 规范 §6.1/§8）：Idempotency-Key → 响应快照，同键同 body 重放同一响应，同键异 body 返 409
CREATE TABLE bp_idempotency_record (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    scope_key           VARCHAR(255)    NOT NULL COMMENT '作用域键：租户|用户|幂等键|方法|路径',
    request_fingerprint VARCHAR(64)     NOT NULL COMMENT '请求载荷指纹（SHA-256）',
    snapshot_json       JSON            NOT NULL COMMENT '响应快照（状态码 / Location / 响应体）',
    expires_at          DATETIME(3)     NOT NULL COMMENT '过期时间（TTL 24h，过后同一键可复用）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_tenant_scope (tenant_id, scope_key),
    KEY idx_idempotency_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等请求快照（Idempotency-Key）';

CREATE TABLE int_template (
    id                  BIGINT          NOT NULL COMMENT '模板主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(100)    NOT NULL COMMENT '模板编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    category            VARCHAR(50)     NOT NULL COMMENT '分类',
    source_type         VARCHAR(50)     NOT NULL COMMENT '源连接器类型',
    target_type         VARCHAR(50)     NOT NULL COMMENT '目标连接器类型',
    default_nodes       JSON            DEFAULT NULL COMMENT '默认节点定义',
    use_count           BIGINT          NOT NULL DEFAULT 0 COMMENT '使用次数',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_int_tpl_code (tenant_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='集成模板表';

-- ============================================================
-- 4. Metadata
-- ============================================================

DROP TABLE IF EXISTS meta_data_quality_rule;
DROP TABLE IF EXISTS meta_code_template;
DROP TABLE IF EXISTS meta_entity_relation;
DROP TABLE IF EXISTS meta_field;
DROP TABLE IF EXISTS meta_entity;

CREATE TABLE meta_entity (
    id                  BIGINT          NOT NULL COMMENT '实体主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '实体名称',
    code                VARCHAR(200)    NOT NULL COMMENT '实体编码',
    display_name        VARCHAR(200)    NOT NULL COMMENT '显示名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    table_name          VARCHAR(200)    NOT NULL COMMENT '对应数据库表名',
    type                TINYINT         NOT NULL DEFAULT 0 COMMENT '0-普通 1-主数据',
    delivery_mode       TINYINT         NOT NULL DEFAULT 0 COMMENT '0-GENERATIVE生成式 1-RUNTIME运行时',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布 2-已归档',
    module_id           BIGINT          DEFAULT NULL COMMENT '所属模块ID（bone_module.id）',
    is_builtin          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否内置',
    icon                VARCHAR(100)    DEFAULT NULL COMMENT '图标',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_e_code (tenant_id, code),
    UNIQUE KEY uk_meta_e_table (tenant_id, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据实体表';

CREATE TABLE meta_field (
    id                  BIGINT          NOT NULL COMMENT '字段主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    entity_id           BIGINT          NOT NULL COMMENT '所属实体ID',
    module_id           BIGINT          DEFAULT NULL COMMENT '所属模块ID（bone_module.id）',
    name                VARCHAR(100)    NOT NULL COMMENT '字段名称',
    code                VARCHAR(100)    NOT NULL COMMENT '字段编码',
    display_name        VARCHAR(200)    NOT NULL COMMENT '显示名称',
    type                VARCHAR(50)     NOT NULL COMMENT '字段类型',
    length              INT             DEFAULT NULL COMMENT '字段长度',
    numeric_precision   INT             DEFAULT NULL COMMENT '小数精度',
    is_required         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填',
    is_unique           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否唯一',
    is_pk               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否主键',
    is_indexed          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否建索引',
    default_value       VARCHAR(500)    DEFAULT NULL COMMENT '默认值',
    enum_values         JSON            DEFAULT NULL COMMENT '枚举值',
    validation_rules    JSON            DEFAULT NULL COMMENT '校验规则',
    comment             VARCHAR(500)    DEFAULT NULL COMMENT '字段注释',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_f_entity_code (entity_id, code),
    KEY idx_meta_f_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据字段表';

CREATE TABLE meta_entity_relation (
    id                  BIGINT          NOT NULL COMMENT '关系主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '关系名称',
    source_entity_id    BIGINT          NOT NULL COMMENT '源实体ID',
    target_entity_id    BIGINT          NOT NULL COMMENT '目标实体ID',
    type                VARCHAR(20)     NOT NULL COMMENT 'OneToOne/OneToMany/ManyToOne/ManyToMany',
    source_field_id     BIGINT          DEFAULT NULL COMMENT '源关联字段ID',
    target_field_id     BIGINT          DEFAULT NULL COMMENT '目标关联字段ID',
    foreign_key_field   VARCHAR(100)    DEFAULT NULL COMMENT '外键字段名',
    is_required         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填',
    cascade_type        VARCHAR(50)     DEFAULT NULL COMMENT '级联类型',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_er_name (tenant_id, name),
    KEY idx_meta_er_source (source_entity_id),
    KEY idx_meta_er_target (target_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实体关系表';

CREATE TABLE meta_code_template (
    id                  BIGINT          NOT NULL COMMENT '模板主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(200)    NOT NULL COMMENT '模板编码',
    type                VARCHAR(50)     NOT NULL COMMENT '模板类型',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    version             VARCHAR(50)     NOT NULL COMMENT '版本号',
    content             MEDIUMTEXT      NOT NULL COMMENT '模板内容',
    engine              VARCHAR(20)     NOT NULL DEFAULT 'Freemarker' COMMENT '模板引擎',
    sample_output       MEDIUMTEXT      DEFAULT NULL COMMENT '示例输出',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布 2-废弃',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_ct_code_version (tenant_id, code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='元数据代码生成模板表';

CREATE TABLE meta_data_quality_rule (
    id                  BIGINT          NOT NULL COMMENT '规则主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    entity_id           BIGINT          NOT NULL COMMENT '所属实体ID',
    name                VARCHAR(200)    NOT NULL COMMENT '规则名称',
    type                VARCHAR(50)     NOT NULL COMMENT '规则类型',
    expression          TEXT            NOT NULL COMMENT '规则表达式',
    severity            TINYINT         NOT NULL DEFAULT 1 COMMENT '严重级别',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_checked_at     DATETIME(3)     DEFAULT NULL COMMENT '最后检查时间',
    violation_count     BIGINT          NOT NULL DEFAULT 0 COMMENT '违规数',
    created_by           BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by           BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_meta_dqr_entity (entity_id),
    UNIQUE KEY uk_meta_dqr_tenant_name (tenant_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量规则表';

-- ============================================================
-- 5. Extension Studio（exts_*）
-- ============================================================

DROP TABLE IF EXISTS exts_audit_log;
DROP TABLE IF EXISTS exts_plugin_execution_log;
DROP TABLE IF EXISTS exts_plugin_version;
DROP TABLE IF EXISTS exts_extension_impl;
DROP TABLE IF EXISTS exts_extension_point;

CREATE TABLE exts_extension_point (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    point_name          VARCHAR(255)    NOT NULL COMMENT '扩展点名称',
    point_code          VARCHAR(200)    NOT NULL COMMENT '扩展点编码（通常为接口 FQCN）',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    interface_name      VARCHAR(500)    NOT NULL COMMENT '扩展点接口 FQCN',
    biz_domain          VARCHAR(100)    DEFAULT NULL COMMENT '业务域',
    category            VARCHAR(100)    DEFAULT NULL COMMENT '分类',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_ep_code (tenant_id, point_code),
    KEY idx_exts_ep_tenant (tenant_id, status),
    KEY idx_exts_ep_iface (interface_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Studio 扩展点';

CREATE TABLE exts_extension_impl (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    extension_point_id  BIGINT          NOT NULL COMMENT '扩展点ID',
    impl_name           VARCHAR(255)    NOT NULL COMMENT '实现名称',
    impl_code           VARCHAR(200)    NOT NULL COMMENT '实现编码',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    class_name          VARCHAR(500)    NOT NULL COMMENT '实现类 FQCN',
    tenant_code         VARCHAR(64)     DEFAULT 'DEFAULT' COMMENT '租户码',
    biz_code            VARCHAR(64)     DEFAULT '*' COMMENT '业务码',
    use_case            VARCHAR(64)     DEFAULT '*' COMMENT '用例',
    scenario            VARCHAR(64)     DEFAULT '*' COMMENT '场景',
    user_group          VARCHAR(64)     DEFAULT '*' COMMENT '用户组',
    priority            INT             NOT NULL DEFAULT 100 COMMENT '优先级',
    config_json         JSON            DEFAULT NULL COMMENT '配置 JSON',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否默认实现',
    rollout_percent     TINYINT         DEFAULT NULL COMMENT '灰度百分比',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_ei_code (extension_point_id, impl_code),
    KEY idx_exts_ei_tenant (tenant_id, extension_point_id),
    KEY idx_exts_ei_point (extension_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Studio 扩展实现';

CREATE TABLE exts_plugin_version (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    plugin_id           BIGINT          NOT NULL COMMENT '插件/实现ID',
    release_version     VARCHAR(50)     NOT NULL COMMENT '发布版本号',
    file_path           VARCHAR(512)    NOT NULL COMMENT '制品路径',
    file_size           BIGINT          DEFAULT NULL COMMENT '文件大小',
    checksum            VARCHAR(64)     NOT NULL COMMENT '校验和',
    is_active           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否当前激活版本',
    deployment_status   VARCHAR(20)     NOT NULL DEFAULT 'STAGED' COMMENT '制品状态 UPLOADED/VALIDATED/STAGED/ACTIVE/DEPRECATED',
    change_log          VARCHAR(500)    DEFAULT NULL COMMENT '变更说明',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_pv (plugin_id, release_version),
    KEY idx_exts_pv_tenant (tenant_id, plugin_id),
    KEY idx_exts_pv_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Studio 插件版本';

CREATE TABLE exts_plugin_execution_log (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    plugin_id           BIGINT          NOT NULL COMMENT '插件ID',
    extension_point_id  BIGINT          NOT NULL COMMENT '扩展点ID',
    execution_id        VARCHAR(64)     NOT NULL COMMENT '执行ID',
    status              VARCHAR(20)     NOT NULL COMMENT '执行状态',
    input_data          JSON            DEFAULT NULL COMMENT '输入',
    output_data         JSON            DEFAULT NULL COMMENT '输出',
    error_message       TEXT            DEFAULT NULL COMMENT '错误信息',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '耗时毫秒',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_pel_exec (execution_id),
    KEY idx_exts_pel_tenant (tenant_id, plugin_id, created_at),
    KEY idx_exts_pel_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Studio 插件执行日志';

CREATE TABLE exts_audit_log (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    trace_id            VARCHAR(64)     DEFAULT NULL COMMENT '链路ID',
    user_id             VARCHAR(64)     DEFAULT NULL COMMENT '操作用户ID',
    action              VARCHAR(80)     NOT NULL COMMENT '操作动作',
    resource_type       VARCHAR(50)     DEFAULT NULL COMMENT '资源类型',
    resource_id         VARCHAR(100)    DEFAULT NULL COMMENT '资源ID',
    result              VARCHAR(20)     NOT NULL COMMENT '结果 SUCCESS/FAILED',
    detail              TEXT            DEFAULT NULL COMMENT '详情',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_exts_audit_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Studio 审计日志';

-- ============================================================
-- 6. Studio Generator（gen_*，bone-engine/studio-generator）
-- ============================================================

DROP TABLE IF EXISTS gen_code_generation_history;
DROP TABLE IF EXISTS gen_generation_task;
DROP TABLE IF EXISTS gen_column_metadata;
DROP TABLE IF EXISTS gen_table_metadata;
DROP TABLE IF EXISTS gen_code_template;
DROP TABLE IF EXISTS gen_type_mapping;
DROP TABLE IF EXISTS gen_data_source;

CREATE TABLE gen_data_source (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(100)    NOT NULL COMMENT '数据源名称',
    db_type             VARCHAR(32)     NOT NULL COMMENT '数据库类型 MYSQL/POSTGRESQL/...',
    host                VARCHAR(255)    NOT NULL COMMENT '主机',
    port                INT             NOT NULL COMMENT '端口',
    db_name             VARCHAR(128)    NOT NULL COMMENT '库名',
    username            VARCHAR(128)    NOT NULL COMMENT '用户名',
    password_encrypted  VARCHAR(512)    NOT NULL COMMENT '密码（加密存储）',
    params              VARCHAR(500)    DEFAULT NULL COMMENT 'JDBC 附加参数',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    last_test_at        DATETIME(3)     DEFAULT NULL COMMENT '最近连通测试时间',
    last_test_result    VARCHAR(20)     DEFAULT NULL COMMENT '最近测试结果 SUCCESS/FAILED',
    last_test_message   VARCHAR(500)    DEFAULT NULL COMMENT '最近测试消息',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_ds_tenant_name (tenant_id, name),
    KEY idx_gen_ds_tenant (tenant_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成数据源';

CREATE TABLE gen_table_metadata (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    data_source_id      VARCHAR(64)     NOT NULL COMMENT '数据源ID（与 gen_data_source 或过渡期字符串ID）',
    table_schema        VARCHAR(128)    DEFAULT NULL COMMENT '库/schema',
    original_table_name VARCHAR(128)    NOT NULL COMMENT '物理表名',
    custom_entity_name  VARCHAR(128)    DEFAULT NULL COMMENT '自定义实体名',
    module_name         VARCHAR(100)    DEFAULT NULL COMMENT '模块名',
    table_comment       VARCHAR(500)    DEFAULT NULL COMMENT '表注释',
    sync_status         VARCHAR(20)     NOT NULL DEFAULT 'SYNCED' COMMENT '同步状态',
    last_sync_at        DATETIME(3)     DEFAULT NULL COMMENT '最近同步时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_tm_ds_table (tenant_id, data_source_id, original_table_name),
    KEY idx_gen_tm_ds (data_source_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成表元数据';

CREATE TABLE gen_column_metadata (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    table_metadata_id   BIGINT          NOT NULL COMMENT '所属 gen_table_metadata.id',
    original_column_name VARCHAR(128)   NOT NULL COMMENT '物理列名',
    custom_field_name   VARCHAR(128)    DEFAULT NULL COMMENT '自定义字段名',
    jdbc_type           VARCHAR(64)     DEFAULT NULL COMMENT 'JDBC 类型名',
    java_type           VARCHAR(128)    DEFAULT NULL COMMENT 'Java 类型',
    column_type         VARCHAR(64)     DEFAULT NULL COMMENT '数据库列类型',
    column_length       INT             DEFAULT NULL COMMENT '长度',
    precision_value     INT             DEFAULT NULL COMMENT '精度',
    scale_value         INT             DEFAULT NULL COMMENT '小数位',
    is_nullable         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否可空',
    is_primary_key      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否主键',
    is_autoincrement    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否自增',
    default_value       VARCHAR(500)    DEFAULT NULL COMMENT '默认值',
    column_comment      VARCHAR(500)    DEFAULT NULL COMMENT '列注释',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_gen_cm_table (table_metadata_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成列元数据';

CREATE TABLE gen_code_template (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(200)    NOT NULL COMMENT '模板编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    type                VARCHAR(50)     NOT NULL COMMENT '模板类型',
    language            VARCHAR(20)     NOT NULL DEFAULT 'java' COMMENT '语言',
    engine              VARCHAR(20)     NOT NULL DEFAULT 'FREEMARKER' COMMENT '模板引擎',
    template_version    VARCHAR(50)     NOT NULL DEFAULT '1.0.0' COMMENT '模板语义版本',
    content             MEDIUMTEXT      NOT NULL COMMENT '模板内容',
    sample_output       MEDIUMTEXT      DEFAULT NULL COMMENT '示例输出',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_ct_code_ver (tenant_id, code, template_version),
    KEY idx_gen_ct_tenant (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成模板';

-- ------------------------------------------------------------
-- studio-generator 内置模板种子（E-3.7）：type/code 与 classpath templates/{code}.ftl 对齐
-- 生成运行时读 classpath .ftl；本表 content 供预览/校验与任务勾选。tenant_id=0 平台租户。
-- ------------------------------------------------------------
INSERT INTO gen_code_template (
    id, tenant_id, name, code, description, type, language, engine, template_version,
    content, sample_output, status, published_at, created_by, updated_by, deleted, version
) VALUES
(910000000000000001, 0, 'Java 实体', 'entity', '生成 domain 实体（@Table AggregateRoot）', 'entity', 'java', 'FREEMARKER', '1.0.0',
    'package ${utils.getPackagePath(basePackage, moduleName)}.domain.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ${table.tableComment!''实体''}。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("${table.originalTableName}")
public class ${table.customEntityName} extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;
<#list columns as column>
  <#if column.isPrimaryKey>
  /** ${column.columnComment!''主键''} */
  @Column(name = "${column.originalColumnName}")
  private ${column.javaType} ${utils.toFieldName(column.originalColumnName)};
  <#else>
  /** ${column.columnComment!''''} */
  @Column(name = "${column.originalColumnName}")
  private ${column.javaType} ${utils.toFieldName(column.originalColumnName)};
  </#if>
</#list>

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0),
(910000000000000002, 0, '仓储接口', 'repository', '生成 domain/repository（含 findPage，不预建 QueryPort）', 'repository', 'java', 'FREEMARKER', '1.0.0',
    'package ${utils.getPackagePath(basePackage, moduleName)}.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.entity.${table.customEntityName};

/**
 * ${table.tableComment!''实体''}仓储。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。本聚合分页留在域仓储，不另建 QueryPort。
 */
public interface ${table.customEntityName}Repository extends Repository<${table.customEntityName}, Long> {

  default PageResult<${table.customEntityName}> findPage(int pageNum, int pageSize) {
    return pageByCriteria(
        Criteria.<${table.customEntityName}>create()
            .orderByDesc(${table.customEntityName}::getId)
            .page(pageNum, pageSize));
  }
}
', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0),
(910000000000000003, 0, '应用服务', 'applicationService', '生成 ApplicationService（Controller→ApplicationService→Repository）', 'applicationService', 'java', 'FREEMARKER', '1.0.0',
    'package ${utils.getPackagePath(basePackage, moduleName)}.application;

import com.bone.core.model.PageResult;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.entity.${table.customEntityName};
import ${utils.getPackagePath(basePackage, moduleName)}.domain.repository.${table.customEntityName}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${table.tableComment!''实体''}应用服务。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。简单读写走本服务，不生成 CommandHandler / QueryHandler。
 */
@Service
@RequiredArgsConstructor
public class ${table.customEntityName}ApplicationService {

  private final ${table.customEntityName}Repository repository;

  @Transactional(readOnly = true)
  public ${table.customEntityName} get(Long id) {
    return repository.findById(id);
  }

  @Transactional(readOnly = true)
  public PageResult<${table.customEntityName}> page(int page, int size) {
    return repository.findPage(page, size);
  }
}
', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0),
(910000000000000004, 0, 'Web 控制器', 'controller', '生成 adapter Controller，注入 ApplicationService', 'controller', 'java', 'FREEMARKER', '1.0.0',
    'package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.core.web.PlatformApiPaths;
import ${utils.getPackagePath(basePackage, moduleName)}.application.${table.customEntityName}ApplicationService;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.entity.${table.customEntityName};
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ${table.tableComment!''实体''}控制器。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 */
@Tag(name = "${table.tableComment!''实体''}", description = "${table.tableComment!''实体''}管理接口")
@RestController
@RequestMapping(PlatformApiPaths.METADATA_V1 + "/${table.originalTableName}")
@RequiredArgsConstructor
public class ${table.customEntityName}Controller {

  private final ${table.customEntityName}ApplicationService applicationService;

  @Operation(summary = "查询 ${table.tableComment!''实体''} 详情")
  @GetMapping("/{id}")
  public ApiResponse<${table.customEntityName}> getById(@PathVariable Long id) {
    return ApiResponse.success(applicationService.get(id));
  }

  @Operation(summary = "分页查询 ${table.tableComment!''实体''}")
  @GetMapping("/page")
  public ApiResponse<PageResult<${table.customEntityName}>> page(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    return ApiResponse.success(applicationService.page(page, size));
  }
}
', NULL, 'PUBLISHED', NOW(3), 1, 1, 0, 0);


CREATE TABLE gen_type_mapping (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    database_type       VARCHAR(32)     NOT NULL COMMENT '数据库类型',
    jdbc_type_name      VARCHAR(64)     NOT NULL COMMENT 'JDBC 类型',
    java_type           VARCHAR(128)    NOT NULL COMMENT '映射 Java 类型',
    precision_expr      VARCHAR(200)    DEFAULT NULL COMMENT '精度表达式',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_type_map (tenant_id, database_type, jdbc_type_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JDBC 到 Java 类型映射';

CREATE TABLE gen_generation_task (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    task_id             VARCHAR(64)     NOT NULL COMMENT '任务业务ID（UUID）',
    project_name        VARCHAR(200)    NOT NULL COMMENT '工程名',
    base_package        VARCHAR(300)    NOT NULL COMMENT '基础包名',
    module_name         VARCHAR(100)    DEFAULT NULL COMMENT '模块名',
    data_source_id      BIGINT          DEFAULT NULL COMMENT '数据源ID',
    table_names         JSON            DEFAULT NULL COMMENT '表名列表',
    template_ids        JSON            DEFAULT NULL COMMENT '模板ID列表',
    gen_config          JSON            DEFAULT NULL COMMENT '生成配置',
    generated_files     JSON            DEFAULT NULL COMMENT '生成文件清单',
    zip_url             VARCHAR(512)    DEFAULT NULL COMMENT '产物 ZIP 地址',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
    error_message       TEXT            DEFAULT NULL COMMENT '失败原因',
    started_at          DATETIME(3)     DEFAULT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_gt_task (task_id),
    KEY idx_gen_gt_tenant (tenant_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成任务';

CREATE TABLE gen_code_generation_history (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    task_id             VARCHAR(64)     NOT NULL COMMENT '关联任务ID',
    template_id         VARCHAR(64)     DEFAULT NULL COMMENT '模板ID',
    template_name       VARCHAR(200)    DEFAULT NULL COMMENT '模板名称',
    generation_name     VARCHAR(200)    DEFAULT NULL COMMENT '生成批次名称',
    data_source_id      VARCHAR(64)     DEFAULT NULL COMMENT '数据源ID',
    table_names         JSON            DEFAULT NULL COMMENT '表名列表',
    base_package        VARCHAR(300)    DEFAULT NULL COMMENT '基础包名',
    module_name         VARCHAR(100)    DEFAULT NULL COMMENT '模块名',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    file_count          INT             DEFAULT NULL COMMENT '生成文件数',
    execution_time      BIGINT          DEFAULT NULL COMMENT '耗时毫秒',
    output_path         VARCHAR(512)    DEFAULT NULL COMMENT '输出路径',
    error_message       TEXT            DEFAULT NULL COMMENT '错误信息',
    started_at          DATETIME(3)     DEFAULT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_gen_cgh_task (task_id),
    KEY idx_gen_cgh_tenant (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代码生成历史';

-- ============================================================
-- 7. 统一控制台（cnsl_*）
-- ============================================================

DROP TABLE IF EXISTS cnsl_recent_access;
DROP TABLE IF EXISTS cnsl_notification;
DROP TABLE IF EXISTS cnsl_dashboard_widget;

CREATE TABLE cnsl_dashboard_widget (
    id                  BIGINT          NOT NULL COMMENT 'Widget主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    type                VARCHAR(50)     NOT NULL COMMENT 'Widget类型',
    config              JSON            NOT NULL COMMENT 'Widget配置',
    position_x          INT             NOT NULL DEFAULT 0 COMMENT 'X轴位置',
    position_y          INT             NOT NULL DEFAULT 0 COMMENT 'Y轴位置',
    width               INT             NOT NULL DEFAULT 4 COMMENT '宽度（栅格）',
    height              INT             NOT NULL DEFAULT 1 COMMENT '高度（行）',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_cnsl_dw_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='仪表盘Widget配置';

CREATE TABLE cnsl_notification (
    id                  BIGINT          NOT NULL COMMENT '通知主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    type                VARCHAR(50)     NOT NULL COMMENT '通知类型',
    title               VARCHAR(200)    NOT NULL COMMENT '通知标题',
    content             TEXT            DEFAULT NULL COMMENT '通知内容',
    action_url          VARCHAR(500)    DEFAULT NULL COMMENT '操作链接',
    is_read             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已读',
    read_at             DATETIME(3)     DEFAULT NULL COMMENT '阅读时间',
    source_module       VARCHAR(50)     NOT NULL COMMENT '来源模块',
    source_event_id     VARCHAR(64)     DEFAULT NULL COMMENT '来源事件ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_cnsl_notif_user_read (user_id, is_read),
    KEY idx_cnsl_notif_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知';

CREATE TABLE cnsl_recent_access (
    id                  BIGINT          NOT NULL COMMENT '访问记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_id         VARCHAR(200)    NOT NULL COMMENT '资源ID',
    resource_name       VARCHAR(200)    NOT NULL COMMENT '资源名称',
    access_url          VARCHAR(500)    NOT NULL COMMENT '访问URL',
    accessed_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间',
    PRIMARY KEY (id),
    KEY idx_cnsl_ra_user (user_id, accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='最近访问记录';

-- ============================================================
-- 8. 主数据管理（mdm_*，bone-platform/bone-masterdata）
-- ============================================================

DROP TABLE IF EXISTS mdm_qcheck_report;
DROP TABLE IF EXISTS mdm_qcheck_detail;
DROP TABLE IF EXISTS mdm_qcheck_task;
DROP TABLE IF EXISTS mdm_record_category;
DROP TABLE IF EXISTS mdm_category;
DROP TABLE IF EXISTS mdm_record_version;
DROP TABLE IF EXISTS mdm_record;
DROP TABLE IF EXISTS mdm_entity;

CREATE TABLE mdm_entity (
    id                  BIGINT          NOT NULL COMMENT '主数据实体主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    meta_entity_id      BIGINT          NOT NULL COMMENT '关联 meta_entity.id',
    entity_code         VARCHAR(200)    NOT NULL COMMENT '主数据实体编码',
    entity_name         VARCHAR(200)    NOT NULL COMMENT '显示名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    is_versioning       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用版本控制',
    workflow_enabled    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用审批流',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_entity_code (tenant_id, entity_code),
    UNIQUE KEY uk_mdm_entity_meta (tenant_id, meta_entity_id),
    KEY idx_mdm_entity_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据实体扩展配置';

CREATE TABLE mdm_record (
    id                  BIGINT          NOT NULL COMMENT '记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    mdm_entity_id       BIGINT          NOT NULL COMMENT '主数据实体ID',
    record_code         VARCHAR(200)    NOT NULL COMMENT '业务唯一编码',
    display_name        VARCHAR(500)    NOT NULL COMMENT '显示名称',
    current_data        JSON            NOT NULL COMMENT '当前生效属性数据',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    version_number      INT             NOT NULL DEFAULT 1 COMMENT '当前版本号',
    effective_from      DATETIME(3)     DEFAULT NULL COMMENT '生效开始时间',
    effective_to        DATETIME(3)     DEFAULT NULL COMMENT '生效结束时间',
    is_current          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否当前有效版本',
    parent_record_id    BIGINT          DEFAULT NULL COMMENT '父记录ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_record_code (tenant_id, mdm_entity_id, record_code),
    KEY idx_mdm_record_entity (mdm_entity_id),
    KEY idx_mdm_record_status (status),
    KEY idx_mdm_record_parent (parent_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录';

CREATE TABLE mdm_record_version (
    id                  BIGINT          NOT NULL COMMENT '版本历史主键（Snowflake）',
    record_id           BIGINT          NOT NULL COMMENT '关联记录ID',
    version_number      INT             NOT NULL COMMENT '版本号',
    data                JSON            NOT NULL COMMENT '属性快照',
    status              VARCHAR(20)     NOT NULL COMMENT '版本状态',
    change_description  TEXT            DEFAULT NULL COMMENT '变更说明',
    approved_by         BIGINT          DEFAULT NULL COMMENT '审批人ID',
    approved_at         DATETIME(3)     DEFAULT NULL COMMENT '审批时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_rv_record_version (record_id, version_number),
    KEY idx_mdm_rv_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录版本历史';

CREATE TABLE mdm_category (
    id                  BIGINT          NOT NULL COMMENT '分类主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    mdm_entity_id       BIGINT          NOT NULL COMMENT '主数据实体ID',
    name                VARCHAR(200)    NOT NULL COMMENT '分类名称',
    code                VARCHAR(100)    NOT NULL COMMENT '分类编码',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    parent_category_id  BIGINT          DEFAULT NULL COMMENT '父分类ID',
    level               INT             NOT NULL DEFAULT 0 COMMENT '层级',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_cat_code (tenant_id, mdm_entity_id, code),
    KEY idx_mdm_cat_parent (parent_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据分类';

CREATE TABLE mdm_record_category (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    record_id           BIGINT          NOT NULL COMMENT '主数据记录ID',
    category_id         BIGINT          NOT NULL COMMENT '分类ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_rc_record_category (record_id, category_id),
    KEY idx_mdm_rc_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录分类关联';

CREATE TABLE mdm_qcheck_task (
    id                  BIGINT          NOT NULL COMMENT '检查任务主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    mdm_entity_id       BIGINT          NOT NULL COMMENT '主数据实体ID',
    check_name          VARCHAR(200)    NOT NULL COMMENT '检查任务名称',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    total_records       INT             NOT NULL DEFAULT 0 COMMENT '检查记录总数',
    passed_records      INT             NOT NULL DEFAULT 0 COMMENT '通过记录数',
    failed_records      INT             NOT NULL DEFAULT 0 COMMENT '失败记录数',
    parameters          JSON            DEFAULT NULL COMMENT '检查参数',
    started_at          DATETIME(3)     DEFAULT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    error_message       TEXT            DEFAULT NULL COMMENT '失败原因',
    created_by          BIGINT          DEFAULT NULL COMMENT '触发人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_mdm_qctask_entity (mdm_entity_id),
    KEY idx_mdm_qctask_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据质量检查任务';

CREATE TABLE mdm_qcheck_detail (
    id                  BIGINT          NOT NULL COMMENT '明细主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    check_id            BIGINT          NOT NULL COMMENT '质量检查任务ID',
    rule_id             BIGINT          NOT NULL COMMENT '质量规则ID',
    record_id           BIGINT          NOT NULL COMMENT '主数据记录ID',
    passed              TINYINT(1)      NOT NULL COMMENT '是否通过',
    message             TEXT            DEFAULT NULL COMMENT '检查消息',
    checked_at          DATETIME(3)     NOT NULL COMMENT '检查时间',
    PRIMARY KEY (id),
    KEY idx_mdm_qcd_check (check_id),
    KEY idx_mdm_qcd_rule (rule_id),
    KEY idx_mdm_qcd_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据质量检查明细';

CREATE TABLE mdm_qcheck_report (
    id                  BIGINT          NOT NULL COMMENT '报告主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    check_id            BIGINT          NOT NULL COMMENT '质量检查任务ID',
    report_data         JSON            NOT NULL COMMENT '报告数据',
    issue_count         INT             NOT NULL DEFAULT 0 COMMENT '质量问题数',
    report_url          VARCHAR(500)    DEFAULT NULL COMMENT '报告URL',
    created_by          BIGINT          DEFAULT NULL COMMENT '生成人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_qrpt_check (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据质量检查报告';

-- ============================================================
-- 8.1 主数据运行时兼容表（bone-masterdata @Table md_*，收敛至 mdm_* 见详设）
-- ============================================================

DROP TABLE IF EXISTS md_record;
DROP TABLE IF EXISTS md_entity;
DROP TABLE IF EXISTS meta_data_lineage;
DROP TABLE IF EXISTS meta_data_standard;
DROP TABLE IF EXISTS ntf_message;

CREATE TABLE md_entity (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    meta_entity_id      BIGINT          DEFAULT NULL COMMENT '来源 meta_entity.id（ADR-0002）',
    name                VARCHAR(200)    NOT NULL COMMENT '实体名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    category            VARCHAR(100)    DEFAULT NULL COMMENT '分类',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_md_entity_tenant (tenant_id),
    UNIQUE KEY uk_md_entity_tenant_name (tenant_id, name),
    UNIQUE KEY uk_md_entity_meta (meta_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据实体（运行时兼容）';

CREATE TABLE md_record (
    id                      BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id               BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    master_data_entity_id   BIGINT          NOT NULL COMMENT '主数据实体ID',
    data                    JSON            NOT NULL COMMENT '记录 JSON 数据',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    publish_time            DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_md_record_tenant (tenant_id),
    KEY idx_md_record_entity (master_data_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录（运行时兼容）';

CREATE TABLE IF NOT EXISTS md_field (
    id                      BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id               BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    master_data_entity_id   BIGINT          NOT NULL COMMENT '主数据实体ID',
    name                    VARCHAR(200)    NOT NULL COMMENT '字段名称',
    code                    VARCHAR(200)    NOT NULL COMMENT '字段编码',
    type                    VARCHAR(50)     NOT NULL COMMENT '字段类型',
    length                  INT             DEFAULT NULL COMMENT '字段长度',
    required                TINYINT(1)      DEFAULT 0 COMMENT '是否必填',
    default_value           VARCHAR(500)    DEFAULT NULL COMMENT '默认值',
    description             TEXT            DEFAULT NULL COMMENT '描述',
    sort_order              INT             DEFAULT 0 COMMENT '排序',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    created_by              BIGINT          DEFAULT NULL COMMENT '创建人',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    updated_by              BIGINT          DEFAULT NULL COMMENT '修改人',
    deleted                 TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_md_field_tenant (tenant_id),
    UNIQUE KEY uk_md_field_tenant_entity_name (tenant_id, master_data_entity_id, name),
    KEY idx_md_field_entity (master_data_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据字段';

CREATE TABLE meta_data_lineage (
    id                  BIGINT          NOT NULL COMMENT '血缘记录主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID（SDK 自动过滤/回填）',
    source_entity       VARCHAR(200)    DEFAULT NULL COMMENT '来源实体',
    source_field        VARCHAR(200)    DEFAULT NULL COMMENT '来源字段',
    transform_type      VARCHAR(100)    DEFAULT NULL COMMENT '转换类型',
    target_entity       VARCHAR(200)    DEFAULT NULL COMMENT '目标实体',
    target_field        VARCHAR(200)    DEFAULT NULL COMMENT '目标字段',
    schema_name         VARCHAR(200)    DEFAULT NULL COMMENT 'Schema 名称',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_lineage_tenant (tenant_id),
    KEY idx_lineage_source (source_entity),
    KEY idx_lineage_target (target_entity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据血缘记录';

CREATE TABLE meta_data_standard (
    id                  BIGINT          NOT NULL COMMENT '数据标准主键（分布式ID）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    entity_code         VARCHAR(200)    DEFAULT NULL COMMENT '实体编码',
    field_code          VARCHAR(200)    DEFAULT NULL COMMENT '字段编码',
    rule_type           TINYINT         DEFAULT NULL COMMENT '规则类型（StandardRuleType.code，与 RuleSeverity 同口径）',
    pattern             VARCHAR(500)    DEFAULT NULL COMMENT '规则表达式/模式',
    ref_code            VARCHAR(200)    DEFAULT NULL COMMENT '引用编码',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '描述',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_std_entity (entity_code),
    KEY idx_std_field (field_code),
    KEY idx_std_tenant (tenant_id),
    UNIQUE KEY uk_std_tenant_entity_field (tenant_id, entity_code, field_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据标准';

CREATE TABLE ntf_message (
    id                  BIGINT          NOT NULL COMMENT '站内信主键（分布式ID）',
    user_id             BIGINT          NOT NULL COMMENT '接收用户ID',
    title               VARCHAR(200)    DEFAULT NULL COMMENT '标题',
    content             TEXT            DEFAULT NULL COMMENT '内容',
    level               VARCHAR(20)     DEFAULT NULL COMMENT '级别（INFO/WARN/ERROR）',
    is_read             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已读（0=未读 1=已读）',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_ntf_user (user_id),
    KEY idx_ntf_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信';

-- ============================================================
-- 增量迁移：修复实体与表结构不一致
-- ============================================================

-- sys_alert_event 的 rule_name 列与 status 默认值已在上面的 CREATE 段定义，
-- 全量重建场景下无需增量 ALTER（原 ADD COLUMN IF NOT EXISTS / ALTER COLUMN 为
-- 非 MySQL 语法且冗余，已移除）。

-- ============================================================
-- 9. 应用与模块（bone-platform/bone-application）
-- ============================================================

CREATE TABLE IF NOT EXISTS `bone_application` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `name` VARCHAR(128) NOT NULL COMMENT '应用名称',
  `code` VARCHAR(64) NOT NULL COMMENT '应用编码',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
  `icon` VARCHAR(64) DEFAULT 'appstore' COMMENT '图标',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-启用 1-停用',
  `created_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `updated_at` DATETIME(3) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用表';

CREATE TABLE IF NOT EXISTS `bone_module` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `app_id` BIGINT NOT NULL COMMENT '所属应用ID',
  `name` VARCHAR(128) NOT NULL COMMENT '模块名称',
  `code` VARCHAR(64) NOT NULL COMMENT '模块编码',
  `description` VARCHAR(512) DEFAULT NULL COMMENT '描述',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0-启用 1-停用 2-归档',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序号',
  `created_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `updated_at` DATETIME(3) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_app_id` (`app_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块表';

CREATE TABLE IF NOT EXISTS `bone_app_permission` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
  `app_id` BIGINT NOT NULL COMMENT '应用ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role` VARCHAR(32) NOT NULL COMMENT '应用角色：ADMIN / DEVELOPER / VIEWER',
  `created_by` BIGINT DEFAULT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `updated_at` DATETIME(3) DEFAULT NULL,
  `deleted` TINYINT(1) NOT NULL DEFAULT 0,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user` (`app_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_tenant_id` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用权限表';

-- ------------------------------------------------------------
-- 9.1 应用 / 模块演示种子数据（MVP-08：元数据建模 UI 的「应用 → 模块」导航开箱即有内容）
--
-- 背景：bone_application / bone_module 此前零数据，前端「元数据 / 应用管理」空列表，
-- 联调脚本 Part C 的 `GET /api/v1/apps/{id}/modules` 取不到真实 id 而只能跳过。
-- 这三张表用 CREATE TABLE IF NOT EXISTS（不 DROP），故种子按主键幂等写入。
-- ------------------------------------------------------------
INSERT INTO `bone_application` (`id`, `tenant_id`, `name`, `code`, `description`, `icon`, `status`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`)
VALUES (1, 0, '默认应用', 'default', 'MVP 演示应用（承载元数据采集模块）', 'appstore', 0, 1, NOW(3), NULL, NULL, 0, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

INSERT INTO `bone_module` (`id`, `tenant_id`, `app_id`, `name`, `code`, `description`, `status`, `sort_order`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`)
VALUES (1, 0, 1, '基础模块', 'base', 'MVP 演示模块（实体建模挂在此模块下）', 0, 1, 1, NOW(3), NULL, NULL, 0, 0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `description` = VALUES(`description`);

INSERT INTO `bone_app_permission` (`id`, `tenant_id`, `app_id`, `user_id`, `role`, `created_by`, `created_at`, `updated_by`, `updated_at`, `deleted`, `version`)
VALUES (1, 0, 1, 1, 'ADMIN', 1, NOW(3), NULL, NULL, 0, 0)
ON DUPLICATE KEY UPDATE `role` = VALUES(`role`);
