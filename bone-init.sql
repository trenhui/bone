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

CREATE TABLE md_entity (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    name                VARCHAR(200)    NOT NULL COMMENT '实体名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    category            VARCHAR(100)    DEFAULT NULL COMMENT '分类',
    status              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据实体（运行时兼容）';

CREATE TABLE md_record (
    id                      BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    master_data_entity_id   BIGINT          NOT NULL COMMENT '主数据实体ID',
    data                    JSON            NOT NULL COMMENT '记录 JSON 数据',
    status                  VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态',
    created_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at              DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    publish_time            DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    PRIMARY KEY (id),
    KEY idx_md_record_entity (master_data_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='主数据记录（运行时兼容）';
