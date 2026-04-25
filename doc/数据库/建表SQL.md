```sql
-- ============================================================
-- BONE X Studio v5.0 全量建表脚本（最终版 · 全字段中文注释）
-- 数据库: bone
-- MySQL 8.0+
-- 包含10大核心模块，58张表
-- 生成时间: 2026-04-24
-- ============================================================

CREATE DATABASE IF NOT EXISTS bone
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_bin;

USE bone;

-- ============================================================
-- 1. 统一IAM（前缀 iam）
-- ============================================================

CREATE TABLE iam_tenant (
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

CREATE TABLE iam_account (
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

CREATE TABLE iam_role (
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

CREATE TABLE iam_permission (
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

CREATE TABLE iam_account_role (
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

CREATE TABLE iam_role_permission (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    role_id             BIGINT          NOT NULL COMMENT '角色ID',
    permission_id       BIGINT          NOT NULL COMMENT '权限ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_iam_role_permission (role_id, permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='角色权限关联表';

CREATE TABLE iam_policy (
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

CREATE TABLE iam_audit_log (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='审计日志表（分区）'
PARTITION BY RANGE (YEAR(created_at)) (
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p2027 VALUES LESS THAN (2028),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

CREATE TABLE iam_refresh_token (
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
-- 2. 架构治理中心（前缀 arch）
-- ============================================================

CREATE TABLE arch_blueprint_template (
    id                  BIGINT          NOT NULL COMMENT '模板主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(100)    NOT NULL COMMENT '模板编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    version             VARCHAR(50)     NOT NULL COMMENT '版本号',
    language            VARCHAR(20)     NOT NULL DEFAULT 'java' COMMENT '语言：java,go',
    base_package        VARCHAR(200)    NOT NULL COMMENT '基础包名',
    cqrs_level          VARCHAR(10)     NOT NULL DEFAULT 'L1' COMMENT 'CQRS等级：L1,L2,L3',
    id_type             VARCHAR(30)     NOT NULL DEFAULT 'SnowflakeId' COMMENT 'ID生成策略',
    is_multi_tenant     TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否多租户：0-否，1-是',
    rules               JSON            NOT NULL COMMENT '模板规则配置',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-废弃',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version_lock        INT             NOT NULL DEFAULT 0 COMMENT '模板版本锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_bpt_code_version (tenant_id, code, version),
    KEY idx_arch_bpt_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='Blueprint模板表';

CREATE TABLE arch_ddd_module (
    id                  BIGINT          NOT NULL COMMENT '模块主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    project_id          BIGINT          NOT NULL COMMENT '项目ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模块名称',
    code                VARCHAR(200)    NOT NULL COMMENT '模块编码',
    bounded_context     VARCHAR(200)    NOT NULL COMMENT '所属限界上下文',
    cqrs_level          VARCHAR(10)     NOT NULL DEFAULT 'L1' COMMENT 'CQRS等级：L1,L2,L3',
    base_package        VARCHAR(200)    NOT NULL COMMENT '基础包名',
    id_type             VARCHAR(30)     NOT NULL DEFAULT 'SnowflakeId' COMMENT 'ID类型',
    dsl_content         JSON            DEFAULT NULL COMMENT 'Bone DSL内容',
    generated_files     JSON            DEFAULT NULL COMMENT '生成的文件列表',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待生成，1-已生成，2-已部署，3-有变更',
    ai_generated        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否AI生成：0-否，1-是',
    generation_prompt   TEXT            DEFAULT NULL COMMENT 'AI生成提示词',
    guard_score         DECIMAL(5,2)    DEFAULT NULL COMMENT '架构守护评分',
    guard_report        JSON            DEFAULT NULL COMMENT '架构守护报告',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_ddm_project_code (tenant_id, project_id, code),
    KEY idx_arch_ddm_project (project_id),
    KEY idx_arch_ddm_cqrs (cqrs_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='DDD模块表';

CREATE TABLE arch_aggregate_root (
    id                  BIGINT          NOT NULL COMMENT '聚合根主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    module_id           BIGINT          NOT NULL COMMENT '所属模块ID',
    name                VARCHAR(200)    NOT NULL COMMENT '聚合根名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    fields              JSON            DEFAULT NULL COMMENT '实体字段定义',
    value_objects       JSON            DEFAULT NULL COMMENT '值对象定义',
    behaviors           JSON            DEFAULT NULL COMMENT '聚合行为定义',
    domain_events       JSON            DEFAULT NULL COMMENT '领域事件定义',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_aggr_module_name (module_id, name),
    KEY idx_arch_aggr_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='聚合根表';

CREATE TABLE arch_extension_point (
    id                  BIGINT          NOT NULL COMMENT '扩展点主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    code                VARCHAR(200)    NOT NULL COMMENT '扩展点编码',
    name                VARCHAR(200)    NOT NULL COMMENT '扩展点名称',
    description         TEXT            DEFAULT NULL COMMENT '扩展点描述',
    method_signature    VARCHAR(500)    NOT NULL COMMENT '方法签名',
    return_type         VARCHAR(200)    NOT NULL COMMENT '返回类型',
    parameter_types     JSON            NOT NULL COMMENT '参数类型列表',
    usage_scenario      VARCHAR(100)    NOT NULL DEFAULT 'common' COMMENT '使用场景',
    owner_module        VARCHAR(200)    NOT NULL COMMENT '所属模块',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_ep_code (code),
    KEY idx_arch_ep_owner (owner_module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='架构扩展点表';

CREATE TABLE arch_marketplace_item (
    id                  VARCHAR(64)     NOT NULL COMMENT '市场项目ID（UUID）',
    tenant_id           BIGINT          DEFAULT NULL COMMENT '租户ID，NULL为公共',
    name                VARCHAR(200)    NOT NULL COMMENT '插件名称',
    code                VARCHAR(200)    NOT NULL COMMENT '插件编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    extension_point_code VARCHAR(200)   NOT NULL COMMENT '关联扩展点编码',
    plugin_version      VARCHAR(50)     NOT NULL COMMENT '插件版本',
    author              VARCHAR(200)    NOT NULL COMMENT '作者',
    jar_url             VARCHAR(500)    NOT NULL COMMENT 'JAR包下载URL',
    jar_checksum        VARCHAR(64)     NOT NULL COMMENT 'JAR包SHA256校验',
    route_config        JSON            NOT NULL COMMENT '路由配置',
    metadata            JSON            DEFAULT NULL COMMENT '元数据',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-待审核，1-已上架，2-已下架，3-审核拒绝',
    download_count      BIGINT          NOT NULL DEFAULT 0 COMMENT '下载次数',
    installed_count     BIGINT          NOT NULL DEFAULT 0 COMMENT '安装次数',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_mi_code_version (code, plugin_version),
    KEY idx_arch_mi_ep (extension_point_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='扩展点市场项目表';

CREATE TABLE arch_guard_rule (
    id                  BIGINT          NOT NULL COMMENT '规则主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '规则名称',
    code                VARCHAR(100)    NOT NULL COMMENT '规则编码',
    description         TEXT            DEFAULT NULL COMMENT '规则描述',
    severity            TINYINT         NOT NULL DEFAULT 1 COMMENT '严重级别：0-提示，1-警告，2-阻断',
    is_blocking         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否阻断CI：0-否，1-是',
    is_fixable          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否可自动修复：0-否，1-是',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    rule_config         JSON            NOT NULL COMMENT '规则配置',
    applicable_scopes   JSON            DEFAULT NULL COMMENT '适用范围',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_arch_gr_code (code),
    KEY idx_arch_gr_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='架构守护规则表';

CREATE TABLE arch_guard_report (
    id                  BIGINT          NOT NULL COMMENT '报告主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    project_id          BIGINT          NOT NULL COMMENT '项目ID',
    module_id           BIGINT          DEFAULT NULL COMMENT '模块ID',
    score               DECIMAL(5,2)    NOT NULL COMMENT '架构健康分',
    total_rules         INT             NOT NULL COMMENT '总规则数',
    passed_rules        INT             NOT NULL COMMENT '通过规则数',
    violation_count     INT             NOT NULL DEFAULT 0 COMMENT '违规数',
    is_blocked          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否阻断：0-否，1-是',
    violations_detail   JSON            DEFAULT NULL COMMENT '违规详情',
    fix_suggestions     JSON            DEFAULT NULL COMMENT '修复建议',
    checked_at          DATETIME(3)     NOT NULL COMMENT '检查时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    KEY idx_arch_grp_project (tenant_id, project_id, checked_at),
    KEY idx_arch_grp_module (module_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='守护检查报告表';


-- ============================================================
-- 3. 元数据应用工厂（前缀 meta）
-- ============================================================

CREATE TABLE meta_entity (
    id                  BIGINT          NOT NULL COMMENT '实体主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '实体名称',
    code                VARCHAR(200)    NOT NULL COMMENT '实体编码',
    display_name        VARCHAR(200)    NOT NULL COMMENT '显示名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    table_name          VARCHAR(200)    NOT NULL COMMENT '对应数据库表名',
    type                TINYINT         NOT NULL DEFAULT 0 COMMENT '类型：0-普通实体，1-主数据实体',
    is_builtin          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否内置：0-否，1-是',
    icon                VARCHAR(100)    DEFAULT NULL COMMENT '图标',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_e_code (tenant_id, code),
    UNIQUE KEY uk_meta_e_table (tenant_id, table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='元数据实体表';

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
    is_required         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    is_unique           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否唯一：0-否，1-是',
    is_pk               TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否主键：0-否，1-是',
    is_indexed          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否建索引：0-否，1-是',
    default_value       VARCHAR(500)    DEFAULT NULL COMMENT '默认值',
    enum_values         JSON            DEFAULT NULL COMMENT '枚举值',
    validation_rules    JSON            DEFAULT NULL COMMENT '校验规则',
    comment             VARCHAR(500)    DEFAULT NULL COMMENT '字段注释',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_f_entity_code (entity_id, code),
    KEY idx_meta_f_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='元数据字段表';

CREATE TABLE meta_entity_relation (
    id                  BIGINT          NOT NULL COMMENT '关系主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '关系名称',
    source_entity_id    BIGINT          NOT NULL COMMENT '源实体ID',
    target_entity_id    BIGINT          NOT NULL COMMENT '目标实体ID',
    type                VARCHAR(20)     NOT NULL COMMENT '关系类型：OneToOne,OneToMany,ManyToOne,ManyToMany',
    source_field_id     BIGINT          DEFAULT NULL COMMENT '源关联字段ID',
    target_field_id     BIGINT          DEFAULT NULL COMMENT '目标关联字段ID',
    foreign_key_field   VARCHAR(100)    DEFAULT NULL COMMENT '外键字段名',
    is_required         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否必填：0-否，1-是',
    cascade_type        VARCHAR(50)     DEFAULT NULL COMMENT '级联类型',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_er_name (tenant_id, name),
    KEY idx_meta_er_source (source_entity_id),
    KEY idx_meta_er_target (target_entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='实体关系表';

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
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-废弃',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_meta_ct_code_version (tenant_id, code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='元数据代码生成模板表';

CREATE TABLE meta_data_quality_rule (
    id                  BIGINT          NOT NULL COMMENT '规则主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    entity_id           BIGINT          NOT NULL COMMENT '所属实体ID',
    name                VARCHAR(200)    NOT NULL COMMENT '规则名称',
    type                VARCHAR(50)     NOT NULL COMMENT '规则类型',
    expression          TEXT            NOT NULL COMMENT '规则表达式',
    severity            TINYINT         NOT NULL DEFAULT 1 COMMENT '严重级别：0-提示，1-警告，2-错误',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    last_checked_at     DATETIME(3)     DEFAULT NULL COMMENT '最后检查时间',
    violation_count     BIGINT          NOT NULL DEFAULT 0 COMMENT '违规数',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_meta_dqr_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据质量规则表';


-- ============================================================
-- 4. 系统集成模块（前缀 integ）
-- ============================================================

CREATE TABLE integ_connector (
    id                  VARCHAR(64)     NOT NULL COMMENT '连接器ID（UUID）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '连接器名称',
    type                VARCHAR(50)     NOT NULL COMMENT '连接器类型',
    config              JSON            NOT NULL COMMENT '连接器配置',
    auth_config         JSON            DEFAULT NULL COMMENT '认证配置',
    retry_config        JSON            DEFAULT NULL COMMENT '重试配置',
    pool_config         JSON            DEFAULT NULL COMMENT '连接池配置',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DISCONNECTED' COMMENT '连接状态：CONNECTED,DISCONNECTED,ERROR',
    last_test_at        DATETIME(3)     DEFAULT NULL COMMENT '最后测试时间',
    last_test_result    VARCHAR(20)     DEFAULT NULL COMMENT '最后测试结果：SUCCESS,FAILED',
    last_test_message   TEXT            DEFAULT NULL COMMENT '最后测试消息',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_integ_conn_tenant (tenant_id),
    KEY idx_integ_conn_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='连接器表';

CREATE TABLE integ_flow (
    id                  VARCHAR(64)     NOT NULL COMMENT '流程ID（UUID）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '流程名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '流程状态：DRAFT,ACTIVE,PAUSED,STOPPED,ERROR',
    source_connector_id VARCHAR(64)     NOT NULL COMMENT '源连接器ID',
    source_config       JSON            DEFAULT NULL COMMENT '源端点配置',
    target_connector_id VARCHAR(64)     NOT NULL COMMENT '目标连接器ID',
    target_config       JSON            DEFAULT NULL COMMENT '目标端点配置',
    nodes               JSON            DEFAULT NULL COMMENT '流程节点定义',
    error_handling      JSON            DEFAULT NULL COMMENT '错误处理配置',
    transaction_cfg     JSON            DEFAULT NULL COMMENT '事务配置',
    schedule_cfg        JSON            DEFAULT NULL COMMENT '调度配置',
    monitoring_cfg      JSON            DEFAULT NULL COMMENT '监控配置',
    route_xml           MEDIUMTEXT      DEFAULT NULL COMMENT 'Camel路由XML',
    deployed_at         DATETIME(3)     DEFAULT NULL COMMENT '部署时间',
    last_executed_at    DATETIME(3)     DEFAULT NULL COMMENT '最后执行时间',
    execution_count     BIGINT          NOT NULL DEFAULT 0 COMMENT '执行次数',
    success_count       BIGINT          NOT NULL DEFAULT 0 COMMENT '成功次数',
    failure_count       BIGINT          NOT NULL DEFAULT 0 COMMENT '失败次数',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_integ_flow_tenant (tenant_id),
    KEY idx_integ_flow_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='集成流程表';

CREATE TABLE integ_flow_execution (
    id                  VARCHAR(64)     NOT NULL COMMENT '执行记录ID（UUID）',
    flow_id             VARCHAR(64)     NOT NULL COMMENT '流程ID',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    exchange_id         VARCHAR(128)    DEFAULT NULL COMMENT 'Camel Exchange ID',
    status              VARCHAR(20)     NOT NULL COMMENT '执行状态：RUNNING,COMPLETED,FAILED,TIMEOUT',
    input_data          JSON            DEFAULT NULL COMMENT '输入数据',
    output_data         JSON            DEFAULT NULL COMMENT '输出数据',
    error_message       TEXT            DEFAULT NULL COMMENT '错误消息',
    error_stack         TEXT            DEFAULT NULL COMMENT '错误堆栈',
    started_at          DATETIME(3)     NOT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    duration_ms         BIGINT          DEFAULT NULL COMMENT '执行耗时（毫秒）',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    node_executions     JSON            DEFAULT NULL COMMENT '各节点执行详情',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id, started_at),
    KEY idx_integ_fe_flow (flow_id),
    KEY idx_integ_fe_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流程执行记录表（分区）'
PARTITION BY RANGE (TO_DAYS(started_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

CREATE TABLE integ_dead_letter (
    id                  VARCHAR(64)     NOT NULL COMMENT '死信ID（UUID）',
    flow_id             VARCHAR(64)     NOT NULL COMMENT '流程ID',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    original_message    JSON            DEFAULT NULL COMMENT '原始消息',
    headers             JSON            DEFAULT NULL COMMENT '消息头',
    error_message       TEXT            DEFAULT NULL COMMENT '错误消息',
    error_stack         TEXT            DEFAULT NULL COMMENT '错误堆栈',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING,RETRYING,RESOLVED,FAILED',
    max_retries         INT             NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '当前重试次数',
    next_retry_at       DATETIME(3)     DEFAULT NULL COMMENT '下次重试时间',
    last_error          TEXT            DEFAULT NULL COMMENT '最后错误',
    resolved_at         DATETIME(3)     DEFAULT NULL COMMENT '解决时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_integ_dl_flow (flow_id),
    KEY idx_integ_dl_next_retry (status, next_retry_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='死信队列表';

CREATE TABLE integ_template (
    id                  VARCHAR(64)     NOT NULL COMMENT '模板ID（UUID）',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(100)    NOT NULL COMMENT '模板编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    category            VARCHAR(50)     NOT NULL COMMENT '分类',
    source_type         VARCHAR(50)     NOT NULL COMMENT '源连接器类型',
    target_type         VARCHAR(50)     NOT NULL COMMENT '目标连接器类型',
    default_nodes       JSON            DEFAULT NULL COMMENT '默认节点定义',
    use_count           BIGINT          NOT NULL DEFAULT 0 COMMENT '使用次数',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_integ_tpl_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='集成模板表';


-- ============================================================
-- 5. 事件存储（前缀 evt）
-- ============================================================

CREATE TABLE evt_events (
    id                  BIGINT          NOT NULL COMMENT '事件主键（Snowflake）',
    event_id            VARCHAR(64)     NOT NULL COMMENT '事件UUID',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    aggregate_type      VARCHAR(200)    NOT NULL COMMENT '聚合类型',
    aggregate_id        VARCHAR(100)    NOT NULL COMMENT '聚合ID',
    aggregate_version   BIGINT          NOT NULL COMMENT '聚合版本号',
    event_type          VARCHAR(200)    NOT NULL COMMENT '事件类型',
    data                JSON            NOT NULL COMMENT '事件数据',
    metadata            JSON            DEFAULT NULL COMMENT '事件元数据',
    schema_version      SMALLINT        NOT NULL DEFAULT 1 COMMENT '事件结构版本',
    occurred_at         DATETIME(3)     NOT NULL COMMENT '事件发生时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '存储时间',
    PRIMARY KEY (id, occurred_at),
    UNIQUE KEY uk_evt_events_agg (aggregate_type, aggregate_id, aggregate_version, occurred_at),
    KEY idx_evt_events_tenant (tenant_id),
    KEY idx_evt_events_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='事件存储表（分区）'
PARTITION BY RANGE (TO_DAYS(occurred_at)) (
    PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
    PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
    PARTITION p202603 VALUES LESS THAN (TO_DAYS('2026-04-01')),
    PARTITION p202604 VALUES LESS THAN (TO_DAYS('2026-05-01')),
    PARTITION p202605 VALUES LESS THAN (TO_DAYS('2026-06-01')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

CREATE TABLE evt_snapshots (
    id                  BIGINT          NOT NULL COMMENT '快照主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    aggregate_type      VARCHAR(200)    NOT NULL COMMENT '聚合类型',
    aggregate_id        VARCHAR(100)    NOT NULL COMMENT '聚合ID',
    aggregate_version   BIGINT          NOT NULL COMMENT '快照版本号',
    data                JSON            NOT NULL COMMENT '快照数据',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_evt_snap_agg (aggregate_type, aggregate_id, aggregate_version),
    KEY idx_evt_snap_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='事件快照表';

CREATE TABLE evt_delivery_tracker (
    id                  BIGINT          NOT NULL COMMENT '跟踪主键（Snowflake）',
    event_id            VARCHAR(64)     NOT NULL COMMENT '事件ID',
    consumer_group      VARCHAR(200)    NOT NULL COMMENT '消费者组',
    consumer_id         VARCHAR(200)    NOT NULL COMMENT '消费者ID',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '投递状态：0-待投递，1-已投递，2-已确认，3-失败',
    delivery_count      INT             NOT NULL DEFAULT 0 COMMENT '投递次数',
    last_delivered_at   DATETIME(3)     DEFAULT NULL COMMENT '最后投递时间',
    next_delivery_at    DATETIME(3)     DEFAULT NULL COMMENT '下次投递时间',
    error_message       TEXT            DEFAULT NULL COMMENT '错误消息',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_evt_dt_event_consumer (event_id, consumer_group, consumer_id),
    KEY idx_evt_dt_status (status, next_delivery_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='事件投递追踪表';


-- ============================================================
-- 6. 统一控制台（前缀 cnsl）
-- ============================================================

CREATE TABLE cnsl_dashboard_widget (
    id                  BIGINT          NOT NULL COMMENT 'Widget主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    type                VARCHAR(50)     NOT NULL COMMENT 'Widget类型',
    config              JSON            NOT NULL COMMENT 'Widget配置',
    position_x          INT             NOT NULL DEFAULT 0 COMMENT 'X轴位置',
    position_y          INT             NOT NULL DEFAULT 0 COMMENT 'Y轴位置',
    width               INT             NOT NULL DEFAULT 4 COMMENT '宽度（栅格）',
    height              INT             NOT NULL DEFAULT 1 COMMENT '高度（行）',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    KEY idx_cnsl_dw_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='仪表盘Widget配置表';

CREATE TABLE cnsl_notification (
    id                  BIGINT          NOT NULL COMMENT '通知主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    type                VARCHAR(50)     NOT NULL COMMENT '通知类型',
    title               VARCHAR(200)    NOT NULL COMMENT '通知标题',
    content             TEXT            DEFAULT NULL COMMENT '通知内容',
    action_url          VARCHAR(500)    DEFAULT NULL COMMENT '操作链接',
    is_read             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已读：0-否，1-是',
    read_at             DATETIME(3)     DEFAULT NULL COMMENT '阅读时间',
    source_module       VARCHAR(50)     NOT NULL COMMENT '来源模块',
    source_event_id     VARCHAR(64)     DEFAULT NULL COMMENT '来源事件ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_cnsl_notif_user_read (user_id, is_read),
    KEY idx_cnsl_notif_time (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='通知表';

CREATE TABLE cnsl_recent_access (
    id                  BIGINT          NOT NULL COMMENT '访问记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    user_id             BIGINT          NOT NULL COMMENT '用户ID',
    resource_type       VARCHAR(50)     NOT NULL COMMENT '资源类型',
    resource_id         VARCHAR(200)    NOT NULL COMMENT '资源ID',
    resource_name       VARCHAR(200)    NOT NULL COMMENT '资源名称',
    access_url          VARCHAR(500)    NOT NULL COMMENT '访问URL',
    accessed_at         DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '访问时间',
    PRIMARY KEY (id),
    KEY idx_cnsl_ra_user (user_id, accessed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='最近访问记录表';


-- ============================================================
-- 7. 代码生成器模块（前缀 gen）
-- ============================================================

CREATE TABLE gen_data_source (
    id                  BIGINT          NOT NULL COMMENT '数据源主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '数据源名称',
    type                VARCHAR(50)     NOT NULL COMMENT '数据库类型：mysql,postgresql,oracle,sqlserver',
    host                VARCHAR(255)    NOT NULL COMMENT '主机地址',
    port                INT             NOT NULL COMMENT '端口',
    db_name             VARCHAR(100)    NOT NULL COMMENT '数据库名称',
    username            VARCHAR(100)    NOT NULL COMMENT '连接用户名',
    password_encrypted  VARCHAR(255)    NOT NULL COMMENT '加密后的密码',
    params              JSON            DEFAULT NULL COMMENT '额外连接参数',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    last_test_at        DATETIME(3)     DEFAULT NULL COMMENT '最后测试时间',
    last_test_result    VARCHAR(20)     DEFAULT NULL COMMENT '最后测试结果：SUCCESS,FAILED',
    last_test_message   TEXT            DEFAULT NULL COMMENT '最后测试失败原因',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_ds_tenant_name (tenant_id, name),
    KEY idx_gen_ds_type (type),
    KEY idx_gen_ds_enabled (is_enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据源配置表';

CREATE TABLE gen_code_template (
    id                  BIGINT          NOT NULL COMMENT '模板主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    name                VARCHAR(200)    NOT NULL COMMENT '模板名称',
    code                VARCHAR(200)    NOT NULL COMMENT '模板编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    type                VARCHAR(50)     NOT NULL COMMENT '模板类型：ENTITY,REPOSITORY,SERVICE,CONTROLLER等',
    language            VARCHAR(20)     NOT NULL DEFAULT 'java' COMMENT '目标语言',
    engine              VARCHAR(20)     NOT NULL DEFAULT 'Freemarker' COMMENT '模板引擎',
    version             VARCHAR(50)     NOT NULL COMMENT '模板版本号',
    content             MEDIUMTEXT      NOT NULL COMMENT '模板内容',
    sample_output       MEDIUMTEXT      DEFAULT NULL COMMENT '示例输出',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-废弃',
    published_at        DATETIME(3)     DEFAULT NULL COMMENT '发布时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version_lock        INT             NOT NULL DEFAULT 0 COMMENT '模板版本锁',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_ct_tenant_code_version (tenant_id, code, version),
    KEY idx_gen_ct_type (type),
    KEY idx_gen_ct_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='代码生成模板表';

CREATE TABLE gen_generation_history (
    id                  BIGINT          NOT NULL COMMENT '历史记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    project_name        VARCHAR(200)    NOT NULL COMMENT '项目名称',
    base_package        VARCHAR(200)    NOT NULL COMMENT '基础包路径',
    module_name         VARCHAR(200)    NOT NULL COMMENT '模块名称',
    data_source_id      BIGINT          DEFAULT NULL COMMENT '使用的数据源ID',
    table_names         JSON            NOT NULL COMMENT '生成的表名列表',
    template_ids        JSON            DEFAULT NULL COMMENT '使用的模板ID列表',
    gen_config          JSON            DEFAULT NULL COMMENT '生成配置快照',
    generated_files     JSON            DEFAULT NULL COMMENT '生成的文件清单',
    zip_url             VARCHAR(500)    DEFAULT NULL COMMENT '生成的ZIP包下载地址',
    status              TINYINT         NOT NULL DEFAULT 0 COMMENT '状态：0-生成中，1-成功，2-失败',
    error_message       TEXT            DEFAULT NULL COMMENT '失败原因',
    started_at          DATETIME(3)     NOT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '操作人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    KEY idx_gen_gh_tenant_project (tenant_id, project_name),
    KEY idx_gen_gh_status (status),
    KEY idx_gen_gh_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='代码生成历史表';

CREATE TABLE gen_type_mapping (
    id                  BIGINT          NOT NULL COMMENT '映射主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID，0为全局默认',
    source_db_type      VARCHAR(50)     NOT NULL COMMENT '来源数据库类型：mysql,postgresql等',
    jdbc_type           VARCHAR(50)     NOT NULL COMMENT 'JDBC类型名称，如VARCHAR,INTEGER',
    java_type           VARCHAR(50)     NOT NULL COMMENT '简写Java类型，如String,Integer',
    full_java_type      VARCHAR(255)    NOT NULL COMMENT '全限定Java类型，如java.lang.String',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '排序号',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_tm_tenant_type_jdbc (tenant_id, source_db_type, jdbc_type),
    KEY idx_gen_tm_source_db (source_db_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='数据类型映射表';

CREATE TABLE gen_table_metadata (
    id                  BIGINT          NOT NULL COMMENT '表元数据主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    data_source_id      BIGINT          NOT NULL COMMENT '关联的数据源ID',
    table_schema        VARCHAR(64)     DEFAULT NULL COMMENT 'Schema名称',
    original_table_name VARCHAR(200)    NOT NULL COMMENT '原始数据库表名',
    custom_entity_name  VARCHAR(200)    DEFAULT NULL COMMENT '用户自定义实体名称',
    module_name         VARCHAR(200)    DEFAULT NULL COMMENT '用户划分的模块名称',
    table_comment       VARCHAR(500)    DEFAULT NULL COMMENT '原始表注释',
    sync_status         TINYINT         NOT NULL DEFAULT 0 COMMENT '同步状态：0-已同步，1-结构有变更，2-已删除',
    last_sync_at        DATETIME(3)     DEFAULT NULL COMMENT '最后同步时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_tmd_tenant_ds_table (tenant_id, data_source_id, original_table_name),
    KEY idx_gen_tmd_data_source (data_source_id),
    KEY idx_gen_tmd_sync_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='表结构元数据表';

CREATE TABLE gen_column_metadata (
    id                  BIGINT          NOT NULL COMMENT '列元数据主键（Snowflake）',
    table_id            BIGINT          NOT NULL COMMENT '关联的表元数据ID',
    original_column_name VARCHAR(100)   NOT NULL COMMENT '原始列名',
    custom_field_name   VARCHAR(200)    DEFAULT NULL COMMENT '用户自定义字段名',
    ordinal_position    INT             NOT NULL COMMENT '列顺序（从1开始）',
    data_type           VARCHAR(50)     NOT NULL COMMENT '原始数据库类型',
    column_size         INT             DEFAULT NULL COMMENT '列长度',
    decimal_digits      INT             DEFAULT NULL COMMENT '小数位数',
    is_nullable         TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否可为空：0-否，1-是',
    is_primary_key      TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否主键列：0-否，1-是',
    column_comment      VARCHAR(500)    DEFAULT NULL COMMENT '原始列注释',
    is_required         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '业务是否必填：0-否，1-是',
    is_generated        TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否生成到代码中：0-不生成，1-生成',
    java_type           VARCHAR(255)    DEFAULT NULL COMMENT '映射的Java类型（覆盖默认）',
    type_mapping_id     BIGINT          DEFAULT NULL COMMENT '关联的类型映射ID',
    annotation_config   JSON            DEFAULT NULL COMMENT '自定义注解配置',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_gen_cm_table_column (table_id, original_column_name),
    KEY idx_gen_cm_table_id (table_id),
    KEY idx_gen_cm_java_type (java_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='列元数据表';


-- ============================================================
-- 8. 系统管理模块（前缀 sys）
-- ============================================================

CREATE TABLE sys_config (
    id                  BIGINT          NOT NULL COMMENT '配置主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    config_key          VARCHAR(255)    NOT NULL COMMENT '配置键',
    config_value        TEXT            NOT NULL COMMENT '配置值',
    description         TEXT            DEFAULT NULL COMMENT '配置描述',
    config_type         VARCHAR(50)     NOT NULL DEFAULT 'SYSTEM' COMMENT '配置类型：SYSTEM-系统级，SERVICE-服务级，FEATURE-功能级',
    is_encrypted        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否加密：0-明文，1-AES256加密',
    is_sensitive        TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否敏感：0-否，1-是（前端不可见）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_config_key (tenant_id, config_key),
    KEY idx_sys_config_type (config_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='系统配置表';

CREATE TABLE sys_config_history (
    id                  BIGINT          NOT NULL COMMENT '历史主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    config_id           BIGINT          NOT NULL COMMENT '关联配置ID',
    old_value           TEXT            DEFAULT NULL COMMENT '旧值',
    new_value           TEXT            NOT NULL COMMENT '新值',
    operator_id         BIGINT          NOT NULL COMMENT '操作人ID',
    operator_name       VARCHAR(100)    NOT NULL COMMENT '操作人用户名',
    change_reason       VARCHAR(500)    DEFAULT NULL COMMENT '变更原因',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
    PRIMARY KEY (id),
    KEY idx_sys_ch_config_id (config_id),
    KEY idx_sys_ch_operator (operator_id),
    KEY idx_sys_ch_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='系统配置变更历史表';

CREATE TABLE sys_alert_rule (
    id                  BIGINT          NOT NULL COMMENT '规则主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    rule_name           VARCHAR(255)    NOT NULL COMMENT '规则名称',
    metric              VARCHAR(255)    NOT NULL COMMENT '监控指标：cpu_usage,memory_usage等',
    threshold           DOUBLE          NOT NULL COMMENT '阈值',
    operator            VARCHAR(10)     NOT NULL DEFAULT '>' COMMENT '比较操作符：>,<,>=,<=,==',
    level               VARCHAR(20)     NOT NULL DEFAULT 'WARNING' COMMENT '告警级别：CRITICAL-严重，WARNING-警告，INFO-信息',
    notification_channels JSON         DEFAULT NULL COMMENT '通知渠道列表，如["email","dingtalk"]',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '规则描述',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_sys_ar_tenant (tenant_id),
    KEY idx_sys_ar_enabled (is_enabled),
    KEY idx_sys_ar_metric (metric)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='告警规则表';

CREATE TABLE sys_alert_event (
    id                  BIGINT          NOT NULL COMMENT '事件主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    rule_id             BIGINT          NOT NULL COMMENT '关联告警规则ID',
    metric_value        DOUBLE          NOT NULL COMMENT '触发时的实际值',
    message             TEXT            NOT NULL COMMENT '告警消息',
    status              VARCHAR(20)     NOT NULL DEFAULT 'FIRING' COMMENT '告警状态：FIRING-触发中，RESOLVED-已解决',
    fired_at            DATETIME(3)     NOT NULL COMMENT '告警触发时间',
    resolved_at         DATETIME(3)     DEFAULT NULL COMMENT '告警解决时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '系统自动触发时为NULL',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    KEY idx_sys_ae_rule (rule_id),
    KEY idx_sys_ae_tenant_status (tenant_id, status),
    KEY idx_sys_ae_fired_at (fired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='告警事件表';

CREATE TABLE sys_system_log (
    id                  BIGINT          NOT NULL COMMENT '日志主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    level               VARCHAR(20)     NOT NULL COMMENT '日志级别：ERROR,WARN,INFO,DEBUG,TRACE',
    service_name        VARCHAR(100)    NOT NULL COMMENT '服务名称',
    logger              VARCHAR(255)    DEFAULT NULL COMMENT 'Logger名称',
    message             TEXT            NOT NULL COMMENT '日志消息',
    trace_id            VARCHAR(128)    DEFAULT NULL COMMENT '分布式追踪ID',
    span_id             VARCHAR(128)    DEFAULT NULL COMMENT 'Span ID',
    thread_name         VARCHAR(255)    DEFAULT NULL COMMENT '线程名称',
    exception           MEDIUMTEXT      DEFAULT NULL COMMENT '异常堆栈',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '日志时间',
    PRIMARY KEY (id, created_at),
    KEY idx_sys_log_level (level),
    KEY idx_sys_log_service (service_name),
    KEY idx_sys_log_trace (trace_id),
    KEY idx_sys_log_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='系统日志表（分区）'
PARTITION BY RANGE (YEAR(created_at)) (
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027),
    PARTITION p2027 VALUES LESS THAN (2028),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);


-- ============================================================
-- 9. 扩展管理模块（前缀 ext）
-- ============================================================

CREATE TABLE ext_extension_point (
    id                  BIGINT          NOT NULL COMMENT '扩展点主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    point_name          VARCHAR(255)    NOT NULL COMMENT '扩展点名称',
    point_code          VARCHAR(200)    NOT NULL COMMENT '扩展点编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    type                VARCHAR(50)     NOT NULL DEFAULT 'BEFORE' COMMENT '类型：BEFORE-前置，AFTER-后置，AROUND-环绕',
    target_object       VARCHAR(255)    NOT NULL COMMENT '目标对象，如Order,User',
    status              VARCHAR(20)     NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用，DISABLED-禁用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_ep_code (tenant_id, point_code),
    KEY idx_ext_ep_target (target_object),
    KEY idx_ext_ep_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='应用扩展点表';

CREATE TABLE ext_plugin (
    id                  BIGINT          NOT NULL COMMENT '插件主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    plugin_name         VARCHAR(255)    NOT NULL COMMENT '插件名称',
    plugin_code         VARCHAR(200)    NOT NULL COMMENT '插件编码',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    status              VARCHAR(20)     NOT NULL DEFAULT 'INSTALLED' COMMENT '状态：INSTALLED-已安装，DEPLOYED-已部署，UNINSTALLED-已卸载',
    wasm_path           VARCHAR(512)    DEFAULT NULL COMMENT 'Wasm文件存储路径',
    jar_url             VARCHAR(500)    DEFAULT NULL COMMENT '插件JAR包URL（非Wasm类插件）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_plugin_code (tenant_id, plugin_code),
    KEY idx_ext_plugin_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='插件表';

CREATE TABLE ext_plugin_version (
    id                  BIGINT          NOT NULL COMMENT '版本主键（Snowflake）',
    plugin_id           BIGINT          NOT NULL COMMENT '关联插件ID',
    version             VARCHAR(50)     NOT NULL COMMENT '版本号',
    file_path           VARCHAR(512)    NOT NULL COMMENT '插件文件存储路径（Wasm/JAR）',
    checksum            VARCHAR(64)     NOT NULL COMMENT '文件SHA256校验和',
    is_active           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为当前激活版本：0-否，1-是',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_pv_plugin_version (plugin_id, version),
    KEY idx_ext_pv_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='插件版本表';

CREATE TABLE ext_extension_impl (
    id                  BIGINT          NOT NULL COMMENT '实现主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID，0为全局',
    extension_point_id  BIGINT          NOT NULL COMMENT '扩展点ID',
    plugin_version_id   BIGINT          NOT NULL COMMENT '插件版本ID',
    impl_code           VARCHAR(200)    NOT NULL COMMENT '实现编码，同一扩展点下唯一',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '实现描述',
    match_condition     JSON            DEFAULT NULL COMMENT '匹配条件，如{"tenantId":"*","bizCode":"order","scenario":"vip"}',
    priority            INT             NOT NULL DEFAULT 0 COMMENT '优先级，数字越小优先级越高',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    is_default          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否为默认实现：0-否，1-是',
    rollout_percent     TINYINT         DEFAULT NULL COMMENT '灰度流量百分比，0-100，NULL表示全量',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ext_ei_code (extension_point_id, impl_code),
    KEY idx_ext_ei_point (extension_point_id),
    KEY idx_ext_ei_plugin_version (plugin_version_id),
    KEY idx_ext_ei_priority (extension_point_id, priority)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='扩展点实现表（多维度路由）';

CREATE TABLE ext_route_script (
    id                  BIGINT          NOT NULL COMMENT '脚本主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID，0为全局',
    extension_impl_id   BIGINT          NOT NULL COMMENT '关联扩展点实现ID',
    script_language     VARCHAR(20)     NOT NULL DEFAULT 'groovy' COMMENT '脚本语言：groovy,spel',
    script_content      TEXT            NOT NULL COMMENT '脚本内容，返回布尔值决定是否路由',
    description         VARCHAR(500)    DEFAULT NULL COMMENT '脚本说明',
    sort_order          INT             NOT NULL DEFAULT 0 COMMENT '同一扩展点下脚本执行顺序',
    is_enabled          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    KEY idx_ext_rs_impl (extension_impl_id),
    KEY idx_ext_rs_order (extension_impl_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='扩展点路由脚本表';


-- ============================================================
-- 10. 主数据管理模块（前缀 mdm）
-- ============================================================

CREATE TABLE mdm_entity (
    id                  BIGINT          NOT NULL COMMENT '主数据实体主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    meta_entity_id      BIGINT          NOT NULL COMMENT '关联元数据实体ID（meta_entity.id）',
    entity_code         VARCHAR(200)    NOT NULL COMMENT '主数据实体编码，如CUSTOMER,MATERIAL',
    entity_name         VARCHAR(200)    NOT NULL COMMENT '主数据实体显示名称',
    description         TEXT            DEFAULT NULL COMMENT '描述',
    is_versioning       TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用版本控制：0-否，1-是',
    workflow_enabled    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否启用审批流：0-否，1-是',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_entity_code (tenant_id, entity_code),
    UNIQUE KEY uk_mdm_entity_meta (tenant_id, meta_entity_id),
    KEY idx_mdm_entity_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据实体扩展配置表';

CREATE TABLE mdm_record (
    id                  BIGINT          NOT NULL COMMENT '记录主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    mdm_entity_id       BIGINT          NOT NULL COMMENT '主数据实体ID',
    record_code         VARCHAR(200)    NOT NULL COMMENT '业务唯一编码（如物料编码）',
    display_name        VARCHAR(500)    NOT NULL COMMENT '记录显示名称',
    current_data        JSON            NOT NULL COMMENT '当前生效的属性数据（JSON格式）',
    status              VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT,PENDING,APPROVED,PUBLISHED,ARCHIVED',
    version_number      INT             NOT NULL DEFAULT 1 COMMENT '当前版本号',
    effective_from      DATETIME(3)     DEFAULT NULL COMMENT '生效开始时间',
    effective_to        DATETIME(3)     DEFAULT NULL COMMENT '生效结束时间',
    is_current          TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否当前有效版本：0-否，1-是',
    parent_record_id    BIGINT          DEFAULT NULL COMMENT '父记录ID，用于层次结构',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_record_code (tenant_id, mdm_entity_id, record_code),
    KEY idx_mdm_record_entity (mdm_entity_id),
    KEY idx_mdm_record_status (status),
    KEY idx_mdm_record_parent (parent_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据记录表';

CREATE TABLE mdm_record_version (
    id                  BIGINT          NOT NULL COMMENT '版本历史主键（Snowflake）',
    record_id           BIGINT          NOT NULL COMMENT '关联记录ID',
    version_number      INT             NOT NULL COMMENT '版本号',
    data                JSON            NOT NULL COMMENT '该版本的属性快照',
    status              VARCHAR(20)     NOT NULL COMMENT '版本状态：DRAFT,APPROVED,OBSOLETE',
    change_description  TEXT            DEFAULT NULL COMMENT '变更说明',
    approved_by         BIGINT          DEFAULT NULL COMMENT '审批人ID',
    approved_at         DATETIME(3)     DEFAULT NULL COMMENT '审批时间',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_rv_record_version (record_id, version_number),
    KEY idx_mdm_rv_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据记录版本历史表';

CREATE TABLE mdm_category (
    id                  BIGINT          NOT NULL COMMENT '分类主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
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
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_cat_code (tenant_id, mdm_entity_id, code),
    KEY idx_mdm_cat_parent (parent_category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据分类表';

CREATE TABLE mdm_record_category (
    id                  BIGINT          NOT NULL COMMENT '关联主键（Snowflake）',
    record_id           BIGINT          NOT NULL COMMENT '主数据记录ID',
    category_id         BIGINT          NOT NULL COMMENT '分类ID',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_rc_record_category (record_id, category_id),
    KEY idx_mdm_rc_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据记录分类关联表';

CREATE TABLE mdm_qcheck_task (
    id                  BIGINT          NOT NULL COMMENT '检查任务主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    mdm_entity_id       BIGINT          NOT NULL COMMENT '主数据实体ID',
    check_name          VARCHAR(200)    NOT NULL COMMENT '检查任务名称',
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING,RUNNING,COMPLETED,FAILED',
    total_records       INT             NOT NULL DEFAULT 0 COMMENT '检查记录总数',
    passed_records      INT             NOT NULL DEFAULT 0 COMMENT '通过记录数',
    failed_records      INT             NOT NULL DEFAULT 0 COMMENT '失败记录数',
    parameters          JSON            DEFAULT NULL COMMENT '检查参数配置',
    started_at          DATETIME(3)     DEFAULT NULL COMMENT '开始时间',
    completed_at        DATETIME(3)     DEFAULT NULL COMMENT '完成时间',
    error_message       TEXT            DEFAULT NULL COMMENT '失败原因',
    created_by          BIGINT          DEFAULT NULL COMMENT '触发人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    PRIMARY KEY (id),
    KEY idx_mdm_qctask_entity (mdm_entity_id),
    KEY idx_mdm_qctask_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据质量检查任务表';

CREATE TABLE mdm_qcheck_detail (
    id                  BIGINT          NOT NULL COMMENT '明细主键（Snowflake）',
    check_id            BIGINT          NOT NULL COMMENT '质量检查任务ID',
    rule_id             BIGINT          NOT NULL COMMENT '质量规则ID（meta_data_quality_rule.id）',
    record_id           BIGINT          NOT NULL COMMENT '主数据记录ID',
    passed              TINYINT(1)      NOT NULL COMMENT '是否通过：0-未通过，1-通过',
    message             TEXT            DEFAULT NULL COMMENT '检查消息',
    checked_at          DATETIME(3)     NOT NULL COMMENT '检查时间',
    PRIMARY KEY (id),
    KEY idx_mdm_qcd_check (check_id),
    KEY idx_mdm_qcd_rule (rule_id),
    KEY idx_mdm_qcd_record (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据质量检查明细表';

CREATE TABLE mdm_qcheck_report (
    id                  BIGINT          NOT NULL COMMENT '报告主键（Snowflake）',
    check_id            BIGINT          NOT NULL COMMENT '质量检查任务ID',
    report_data         JSON            NOT NULL COMMENT '报告数据',
    issue_count         INT             NOT NULL DEFAULT 0 COMMENT '质量问题数',
    report_url          VARCHAR(500)    DEFAULT NULL COMMENT '报告文件访问URL',
    created_by          BIGINT          DEFAULT NULL COMMENT '生成人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '生成时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mdm_qrpt_check (check_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='主数据质量检查报告表';


-- ============================================================
-- 脚本结束
-- 共计 58 张核心业务表，涵盖 10 大模块
-- 全部表、字段均附带中文注释，遵循 Bone-Blueprint v14.3 设计规范
-- ============================================================
```