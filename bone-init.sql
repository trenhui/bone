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

-- ============================================================
-- 1. IAM
-- ============================================================

DROP TABLE IF EXISTS iam_refresh_token;
DROP TABLE IF EXISTS iam_policy;
DROP TABLE IF EXISTS iam_account_role;
DROP TABLE IF EXISTS iam_role_permission;
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

INSERT INTO iam_account (id, tenant_id, username, password_hash, email, real_name, status, is_admin)
VALUES (1, 0, 'admin', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', 'admin@bone.com', '系统管理员', 1, 1);

INSERT INTO iam_role (id, tenant_id, name, code, type, description)
VALUES
    (1, 0, '超级管理员', 'SUPER_ADMIN', 0, '系统超级管理员'),
    (2, 0, '普通用户', 'USER', 1, '普通用户');

INSERT INTO iam_account_role (id, tenant_id, account_id, role_id)
VALUES (1, 0, 1, 1);

-- ============================================================
-- 2. System
-- ============================================================

DROP TABLE IF EXISTS sys_alert_event;
DROP TABLE IF EXISTS sys_alert_rule;
DROP TABLE IF EXISTS sys_log;
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
    alert_level         VARCHAR(20)     NOT NULL COMMENT '告警级别',
    metric_name         VARCHAR(100)    NOT NULL COMMENT '指标名称',
    current_value       DECIMAL(20,4)   NOT NULL COMMENT '当前值',
    threshold_value     DECIMAL(20,4)   NOT NULL COMMENT '阈值',
    message             VARCHAR(500)    NOT NULL COMMENT '告警消息',
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/ACK/RESOLVED',
    resolved_at       DATETIME(3)     DEFAULT NULL COMMENT '恢复时间',
    created_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_alert_event_rule (rule_id),
    KEY idx_sys_alert_event_status (status),
    KEY idx_sys_alert_event_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件';

-- ============================================================
-- 3. Integration（对齐 bone-platform/bone-integration 领域模型）
-- ============================================================

DROP TABLE IF EXISTS int_flow_connection;
DROP TABLE IF EXISTS int_flow_node;
DROP TABLE IF EXISTS int_execution_log;
DROP TABLE IF EXISTS int_dead_letter;
DROP TABLE IF EXISTS int_template;
DROP TABLE IF EXISTS int_flow;
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
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '0-草稿 1-已发布 2-已归档',
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
    name                VARCHAR(100)    NOT NULL COMMENT '字段名称',
    code                VARCHAR(100)    NOT NULL COMMENT '字段编码',
    display_name        VARCHAR(200)    NOT NULL COMMENT '显示名称',
    type                VARCHAR(50)     NOT NULL COMMENT '字段类型',
    length              INT             DEFAULT NULL COMMENT '字段长度',
    `precision`         INT             DEFAULT NULL COMMENT '小数精度',
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
    KEY idx_meta_dqr_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据质量规则表';

-- ============================================================
-- 5. Extension Studio（exts_*）
-- ============================================================

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
