-- Testcontainers MySQL 初始化（与 bone-init.sql §5 exts_* 对齐 + Metadata SDK column_allocation）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS column_allocation (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    tenant_id           BIGINT          NOT NULL,
    app_code            VARCHAR(64)     NOT NULL,
    biz_identity_code   VARCHAR(64)     NOT NULL,
    entity_type         VARCHAR(128)    NOT NULL,
    data_type           VARCHAR(20)     NOT NULL,
    column_name         VARCHAR(64)     NOT NULL,
    column_index        INT             NOT NULL,
    status              VARCHAR(64)     NOT NULL,
    version             INT             NOT NULL DEFAULT 0,
    created_by          BIGINT,
    created_at          DATETIME(3)     DEFAULT CURRENT_TIMESTAMP(3),
    updated_by          BIGINT,
    updated_at          DATETIME(3)     DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exts_extension_point (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    point_name          VARCHAR(255)    NOT NULL,
    point_code          VARCHAR(200)    NOT NULL,
    description         TEXT,
    interface_name      VARCHAR(500)    NOT NULL,
    biz_domain          VARCHAR(100),
    category            VARCHAR(100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ENABLED',
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_ep_code (tenant_id, point_code),
    KEY idx_exts_ep_tenant (tenant_id, status),
    KEY idx_exts_ep_iface (interface_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exts_extension_impl (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    extension_point_id  BIGINT          NOT NULL,
    impl_name           VARCHAR(255)    NOT NULL,
    impl_code           VARCHAR(200)    NOT NULL,
    description         VARCHAR(500),
    class_name          VARCHAR(500)    NOT NULL,
    tenant_code         VARCHAR(64)     DEFAULT 'DEFAULT',
    biz_code            VARCHAR(64)     DEFAULT '*',
    use_case            VARCHAR(64)     DEFAULT '*',
    scenario            VARCHAR(64)     DEFAULT '*',
    user_group          VARCHAR(64)     DEFAULT '*',
    priority            INT             NOT NULL DEFAULT 100,
    config_json         JSON,
    status              TINYINT         NOT NULL DEFAULT 1,
    is_default          TINYINT(1)      NOT NULL DEFAULT 0,
    rollout_percent     TINYINT,
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_ei_code (extension_point_id, impl_code),
    KEY idx_exts_ei_tenant (tenant_id, extension_point_id),
    KEY idx_exts_ei_point (extension_point_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exts_plugin_version (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    plugin_id           BIGINT          NOT NULL,
    release_version     VARCHAR(50)     NOT NULL,
    file_path           VARCHAR(512)    NOT NULL,
    file_size           BIGINT,
    checksum            VARCHAR(64)     NOT NULL,
    is_active           TINYINT(1)      NOT NULL DEFAULT 0,
    change_log          VARCHAR(500),
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_pv (plugin_id, release_version),
    KEY idx_exts_pv_tenant (tenant_id, plugin_id),
    KEY idx_exts_pv_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exts_plugin_execution_log (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    plugin_id           BIGINT          NOT NULL,
    extension_point_id  BIGINT          NOT NULL,
    execution_id        VARCHAR(64)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    input_data          JSON,
    output_data         JSON,
    error_message       TEXT,
    duration_ms         BIGINT,
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted             TINYINT(1)      NOT NULL DEFAULT 0,
    version             INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_exts_pel_exec (execution_id),
    KEY idx_exts_pel_tenant (tenant_id, plugin_id, created_at),
    KEY idx_exts_pel_plugin (plugin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS exts_audit_log (
    id                  BIGINT          NOT NULL,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    trace_id            VARCHAR(64),
    user_id             VARCHAR(64),
    action              VARCHAR(80)     NOT NULL,
    resource_type       VARCHAR(50),
    resource_id         VARCHAR(100),
    result              VARCHAR(20)     NOT NULL,
    detail              TEXT,
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_exts_audit_tenant_time (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
