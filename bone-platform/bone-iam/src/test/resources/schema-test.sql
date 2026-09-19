-- IAM 模块容器级装配测试用 schema（H2 / MODE=MySQL）。
-- 由 bone-init.sql 的 iam_* 表 DDL 转写：MySQL 专属语法（COMMENT / ENGINE / UNIQUE KEY /
-- DATETIME(3) / ON UPDATE / 二级 KEY 含前缀索引）H2 不支持，故去注释、唯一键改 CREATE UNIQUE INDEX、
-- 二级索引整体省略（装配测试不做查询计划验证）；INSERT 种子语句不纳入。

CREATE TABLE IF NOT EXISTS iam_tenant (
    id                  BIGINT          NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    code                VARCHAR(50)     NOT NULL,
    level               TINYINT         NOT NULL DEFAULT 0,
    status              TINYINT         NOT NULL DEFAULT 1,
    admin_email         VARCHAR(200)    NOT NULL,
    max_accounts        INT             DEFAULT NULL,
    max_roles           INT             DEFAULT NULL,
    created_by           BIGINT          DEFAULT NULL,
    updated_by           BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_tenant_code ON iam_tenant (code);

CREATE TABLE IF NOT EXISTS iam_account (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    username            VARCHAR(100)    NOT NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    email               VARCHAR(200)    NOT NULL DEFAULT '',
    phone               VARCHAR(20)     DEFAULT NULL,
    real_name           VARCHAR(100)    DEFAULT NULL,
    avatar_url          VARCHAR(500)    DEFAULT NULL,
    status              TINYINT         NOT NULL DEFAULT 1,
    is_admin            TINYINT      NOT NULL DEFAULT 0,
    last_login_at       TIMESTAMP     DEFAULT NULL,
    last_login_ip       VARCHAR(50)     DEFAULT NULL,
    login_fail_count    SMALLINT        NOT NULL DEFAULT 0,
    locked_at        TIMESTAMP     DEFAULT NULL,
    password_updated_at      TIMESTAMP     DEFAULT NULL,
    created_by           BIGINT          DEFAULT NULL,
    updated_by           BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_username ON iam_account (tenant_id, username);

CREATE TABLE IF NOT EXISTS iam_role (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    name                VARCHAR(100)    NOT NULL,
    code                VARCHAR(100)    NOT NULL,
    type                TINYINT         NOT NULL DEFAULT 1,
    description         VARCHAR(500)    DEFAULT NULL,
    parent_role_id      BIGINT          DEFAULT NULL,
    created_by           BIGINT          DEFAULT NULL,
    updated_by           BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_role_code ON iam_role (tenant_id, code);

CREATE TABLE IF NOT EXISTS iam_permission (
    id                  BIGINT          NOT NULL,
    code                VARCHAR(200)    NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    resource_type       VARCHAR(50)     NOT NULL DEFAULT '',
    resource_path       VARCHAR(500)    NOT NULL DEFAULT '',
    action              VARCHAR(50)     NOT NULL DEFAULT '',
    parent_id           BIGINT          DEFAULT NULL,
    type                VARCHAR(50)     NOT NULL DEFAULT 'OPERATION',
    sort_order          INT             NOT NULL DEFAULT 0,
    description         VARCHAR(500)    DEFAULT NULL,
    created_by           BIGINT          DEFAULT NULL,
    updated_by           BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_permission_code ON iam_permission (code);

CREATE TABLE IF NOT EXISTS iam_account_role (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    account_id          BIGINT          NOT NULL,
    role_id             BIGINT          NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_account_role ON iam_account_role (account_id, role_id);

CREATE TABLE IF NOT EXISTS iam_role_permission (
    id                  BIGINT          NOT NULL,
    role_id             BIGINT          NOT NULL,
    permission_id       BIGINT          NOT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_role_permission ON iam_role_permission (role_id, permission_id);

CREATE TABLE IF NOT EXISTS iam_audit_log (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    user_id             BIGINT          DEFAULT NULL,
    operation           VARCHAR(50)     NOT NULL,
    resource_id         VARCHAR(100)    DEFAULT NULL,
    resource_type       VARCHAR(50)     DEFAULT NULL,
    ip                  VARCHAR(45)     DEFAULT NULL,
    user_agent          VARCHAR(255)    DEFAULT NULL,
    parameters          CLOB            DEFAULT NULL,
    result              CLOB            DEFAULT NULL,
    duration            INT             DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS iam_audit_settings (
    id                      BIGINT          NOT NULL,
    tenant_id               BIGINT          NOT NULL DEFAULT 0,
    retention_days          INT             NOT NULL DEFAULT 30,
    auto_archive_enabled    TINYINT      NOT NULL DEFAULT 1,
    archive_after_days      INT             NOT NULL DEFAULT 15,
    storage_type            VARCHAR(32)     NOT NULL DEFAULT 'DATABASE',
    worm_enabled            TINYINT      NOT NULL DEFAULT 0,
    created_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_audit_settings_tenant ON iam_audit_settings (tenant_id);

CREATE TABLE IF NOT EXISTS iam_policy (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    code                VARCHAR(100)    NOT NULL,
    name                VARCHAR(200)    NOT NULL,
    resource_type       VARCHAR(50)     NOT NULL,
    resource_path       VARCHAR(500)    NOT NULL,
    action              VARCHAR(50)     NOT NULL,
    `condition`         JSON            NOT NULL,
    effect              VARCHAR(10)     NOT NULL DEFAULT 'ALLOW',
    priority            INT             NOT NULL DEFAULT 0,
    is_enabled          TINYINT      NOT NULL DEFAULT 1,
    description         VARCHAR(500)    DEFAULT NULL,
    created_by           BIGINT          DEFAULT NULL,
    updated_by           BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_policy_code ON iam_policy (tenant_id, code);

CREATE TABLE IF NOT EXISTS iam_refresh_token (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL,
    account_id          BIGINT          NOT NULL,
    token_hash          VARCHAR(255)    NOT NULL,
    expires_at          TIMESTAMP     NOT NULL,
    is_revoked          TINYINT      NOT NULL DEFAULT 0,
    replaced_by         VARCHAR(255)    DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_refresh_token_hash ON iam_refresh_token (token_hash);

CREATE TABLE IF NOT EXISTS iam_dept (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    name                VARCHAR(200)    NOT NULL,
    parent_id           BIGINT          DEFAULT NULL,
    order_no            INT             NOT NULL DEFAULT 0,
    status              TINYINT         NOT NULL DEFAULT 1,
    created_by          BIGINT          DEFAULT NULL,
    updated_by          BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS iam_menu (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    name                VARCHAR(200)    NOT NULL,
    parent_id           BIGINT          DEFAULT NULL,
    path                VARCHAR(500)    DEFAULT NULL,
    icon                VARCHAR(200)    DEFAULT NULL,
    order_no            INT             NOT NULL DEFAULT 0,
    permission          VARCHAR(200)    DEFAULT NULL,
    type                TINYINT         NOT NULL DEFAULT 1,
    created_by          BIGINT          DEFAULT NULL,
    updated_by          BIGINT          DEFAULT NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             TINYINT      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
