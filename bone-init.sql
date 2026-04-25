-- ============================================================
-- Bone 数据库初始化脚本（修复版）
-- 基于代码实体类逆向生成，与 domain 模型严格一致
-- 数据库: bone
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS bone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE bone;

-- ============================================================
-- 1. IAM 模块（基于 bone-iam domain 实体）
-- ============================================================

/* 1.1 用户表 —— 对应 com.bone.iam.domain.user.User */
DROP TABLE IF EXISTS iam_user;
CREATE TABLE iam_user (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户主键（对应 AggregateRoot.id）',
    username          VARCHAR(50)   NOT NULL UNIQUE COMMENT '用户名（值对象 Username）',
    password_hash     VARCHAR(255)  NOT NULL COMMENT '密码哈希（对应实体字段 passwordHash，BCrypt）',
    email             VARCHAR(100)  COMMENT '邮箱（值对象 Email）',
    status            VARCHAR(20)   NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED/DISABLED',
    tenant_id         BIGINT        COMMENT '租户ID（代码中有 tenantId 字段）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_username (username),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

/* 1.2 角色表 —— 对应 com.bone.iam.domain.role.Role
   注意：Role.id 是领域ID（UUID），dbId 是数据库自增ID */
DROP TABLE IF EXISTS iam_role;
CREATE TABLE iam_role (
    id                VARCHAR(64)   PRIMARY KEY COMMENT '角色领域ID（UUID，对应 RoleId）',
    db_id             BIGINT AUTO_INCREMENT UNIQUE COMMENT '数据库自增ID（代码回填字段 dbId）',
    name              VARCHAR(100)  NOT NULL COMMENT '角色名称（值对象 RoleName）',
    description       VARCHAR(255)  COMMENT '角色描述',
    tenant_id         BIGINT        COMMENT '租户ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_db_id (db_id),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

/* 1.3 权限表 —— 对应 com.bone.iam.domain.permission.Permission
   注意：Permission.id 是领域ID（UUID），dbId 是数据库自增ID */
DROP TABLE IF EXISTS iam_permission;
CREATE TABLE iam_permission (
    id                VARCHAR(64)   PRIMARY KEY COMMENT '权限领域ID（UUID，对应 PermissionId）',
    db_id             BIGINT AUTO_INCREMENT UNIQUE COMMENT '数据库自增ID（代码回填字段 dbId）',
    code              VARCHAR(50)   NOT NULL UNIQUE COMMENT '权限编码（值对象 PermissionCode）',
    name              VARCHAR(100)  NOT NULL COMMENT '权限名称',
    description       VARCHAR(255)  COMMENT '权限描述',
    parent_id         VARCHAR(64)   COMMENT '父权限ID',
    type              VARCHAR(20)   NOT NULL COMMENT '权限类型：MENU/OPERATION/DATA',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_db_id (db_id),
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限';

/* 1.4 用户角色关联表 —— 支撑多对多关系，代码无独立实体 */
DROP TABLE IF EXISTS iam_user_role;
CREATE TABLE iam_user_role (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联主键',
    user_id           BIGINT        NOT NULL COMMENT '用户ID',
    role_id           VARCHAR(64)   NOT NULL COMMENT '角色领域ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uniq_user_role (user_id, role_id),
    INDEX idx_user (user_id),
    INDEX idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

/* 1.5 角色权限关联表 —— 支撑多对多关系，代码无独立实体 */
DROP TABLE IF EXISTS iam_role_permission;
CREATE TABLE iam_role_permission (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联主键',
    role_id           VARCHAR(64)   NOT NULL COMMENT '角色领域ID',
    permission_id     VARCHAR(64)   NOT NULL COMMENT '权限领域ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uniq_role_permission (role_id, permission_id),
    INDEX idx_role (role_id),
    INDEX idx_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联';

/* 1.6 审计日志表 —— 对应 com.bone.iam.domain.audit.AuditLog */
DROP TABLE IF EXISTS iam_audit_log;
CREATE TABLE iam_audit_log (
    id                VARCHAR(64)   PRIMARY KEY COMMENT '审计日志领域ID（UUID，对应 AuditLogId）',
    tenant_id         BIGINT        COMMENT '租户ID',
    user_id           BIGINT        COMMENT '操作用户ID',
    operation         VARCHAR(50)   NOT NULL COMMENT '操作类型（值对象 OperationType）',
    resource_id       VARCHAR(100)  COMMENT '目标资源ID',
    resource_type     VARCHAR(50)   COMMENT '目标资源类型',
    ip                VARCHAR(45)   COMMENT '操作IP',
    user_agent        VARCHAR(255)  COMMENT '用户代理',
    parameters        TEXT          COMMENT '请求参数',
    result            TEXT          COMMENT '操作结果',
    duration          INT           COMMENT '执行耗时（毫秒）',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_operation (operation),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

-- ============================================================
-- 2. System 模块（基于 bone-system domain 实体）
-- ============================================================

/* 2.1 系统配置表 —— 对应 com.bone.system.domain.model.config.SystemConfig
   注意：SystemConfig.id 是领域ID（UUID），dbId 是数据库自增ID */
DROP TABLE IF EXISTS sys_config;
CREATE TABLE sys_config (
    id                VARCHAR(64)   PRIMARY KEY COMMENT '配置领域ID（UUID，对应 ConfigId）',
    db_id             BIGINT AUTO_INCREMENT UNIQUE COMMENT '数据库自增ID（代码回填字段 dbId）',
    config_key        VARCHAR(100)  NOT NULL UNIQUE COMMENT '配置键（值对象 ConfigKey）',
    config_value      TEXT          NOT NULL COMMENT '配置值（值对象 ConfigValue）',
    description       VARCHAR(255)  COMMENT '配置描述',
    config_type       VARCHAR(20)   NOT NULL COMMENT '配置类型：SYSTEM/SERVICE/FEATURE',
    encrypted         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否加密存储',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_db_id (db_id),
    INDEX idx_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置';

/* 2.2 系统日志表 —— 对应 com.bone.system.domain.model.log.SystemLog */
DROP TABLE IF EXISTS sys_log;
CREATE TABLE sys_log (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志主键',
    level             VARCHAR(20)   NOT NULL COMMENT '日志级别：ERROR/WARN/INFO/DEBUG/TRACE',
    service           VARCHAR(100)  NOT NULL COMMENT '服务名称',
    content           TEXT          NOT NULL COMMENT '日志内容',
    trace_id          VARCHAR(100)  COMMENT '追踪ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_level (level),
    INDEX idx_service (service),
    INDEX idx_trace_id (trace_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统日志';

/* 2.3 告警规则表 —— 对应 com.bone.system.domain.model.alert.AlertRule */
DROP TABLE IF EXISTS sys_alert_rule;
CREATE TABLE sys_alert_rule (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则主键',
    name                  VARCHAR(100)  NOT NULL COMMENT '规则名称',
    description           VARCHAR(255)  COMMENT '规则描述',
    metric_name           VARCHAR(100)  NOT NULL COMMENT '指标名称（值对象 MetricName）',
    threshold_value       DECIMAL(20,4) NOT NULL COMMENT '阈值（值对象 Threshold）',
    alert_level           VARCHAR(20)   NOT NULL COMMENT '告警级别：CRITICAL/WARNING/INFO',
    notification_channels JSON          COMMENT '通知渠道列表',
    enabled               TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    create_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time           DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_metric (metric_name),
    INDEX idx_level (alert_level),
    INDEX idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警规则';

/* 2.4 告警事件表 —— 对应 com.bone.system.domain.model.alert.AlertEvent */
DROP TABLE IF EXISTS sys_alert_event;
CREATE TABLE sys_alert_event (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '事件主键',
    rule_id           BIGINT        NOT NULL COMMENT '关联告警规则ID',
    alert_level       VARCHAR(20)   NOT NULL COMMENT '告警级别',
    metric_name       VARCHAR(100)  NOT NULL COMMENT '指标名称',
    current_value     DECIMAL(20,4) NOT NULL COMMENT '当前值',
    threshold_value   DECIMAL(20,4) NOT NULL COMMENT '阈值',
    message           VARCHAR(500)  NOT NULL COMMENT '告警消息',
    status            VARCHAR(20)   NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN/ACK/RESOLVED',
    resolved_time     DATETIME      COMMENT '恢复时间',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rule (rule_id),
    INDEX idx_status (status),
    INDEX idx_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='告警事件';

-- ============================================================
-- 3. Integration 模块（基于 bone-integration domain 实体）
-- ============================================================

/* 3.1 连接器表 —— 对应 com.bone.integration.domain.model.connector.Connector */
DROP TABLE IF EXISTS int_connector;
CREATE TABLE int_connector (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '连接器主键（对应 ConnectorId）',
    name              VARCHAR(100)  NOT NULL COMMENT '连接器名称',
    type              VARCHAR(50)   NOT NULL COMMENT '连接器类型（值对象 ConnectorType）',
    config            JSON          NOT NULL COMMENT '连接器配置（Map<String,Object>）',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DISABLED' COMMENT '状态：ENABLED/DISABLED',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_type (type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连接器';

/* 3.2 集成流程表 —— 对应 com.bone.integration.domain.model.flow.IntegrationFlow
   注意：IntegrationFlow.id 是领域ID（UUID），dbId 是数据库自增ID */
DROP TABLE IF EXISTS int_flow;
CREATE TABLE int_flow (
    id                VARCHAR(64)   PRIMARY KEY COMMENT '流程领域ID（UUID，对应 FlowId）',
    db_id             BIGINT AUTO_INCREMENT UNIQUE COMMENT '数据库自增ID（代码回填字段 dbId）',
    name              VARCHAR(100)  NOT NULL COMMENT '流程名称',
    description       VARCHAR(255)  COMMENT '流程描述',
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT/ACTIVE/INACTIVE/DELETED',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_db_id (db_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集成流程';

/* 3.3 流程执行记录表 —— 对应 com.bone.integration.domain.model.execution.IntegrationLog */
DROP TABLE IF EXISTS int_flow_execution;
CREATE TABLE int_flow_execution (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '执行记录主键（对应 ExecutionLogId）',
    flow_id           VARCHAR(64)   NOT NULL COMMENT '流程领域ID（对应 FlowId）',
    status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT',
    start_time        DATETIME      COMMENT '开始时间',
    end_time          DATETIME      COMMENT '结束时间',
    input_data        TEXT          COMMENT '输入数据',
    output_data       TEXT          COMMENT '输出数据',
    error_message     TEXT          COMMENT '错误信息',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_flow (flow_id),
    INDEX idx_status (status),
    INDEX idx_time (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程执行记录';

