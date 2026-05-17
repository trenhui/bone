-- Studio 扩展点（H2 联调；列序与 bone-init.sql §5 一致；生产 MySQL 仅 bone-init，勿执行本文件）
CREATE TABLE IF NOT EXISTS exts_extension_point (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    point_name          VARCHAR(255)    NOT NULL,
    point_code          VARCHAR(200)    NOT NULL,
    description         CLOB,
    interface_name      VARCHAR(500)    NOT NULL,
    biz_domain          VARCHAR(100),
    category            VARCHAR(100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ENABLED',
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exts_ep_code ON exts_extension_point (tenant_id, point_code);
CREATE INDEX IF NOT EXISTS idx_exts_ep_tenant ON exts_extension_point (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_exts_ep_iface ON exts_extension_point (interface_name);

CREATE TABLE IF NOT EXISTS exts_extension_impl (
    id                  BIGINT          NOT NULL PRIMARY KEY,
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
    config_json         CLOB,
    status              TINYINT         NOT NULL DEFAULT 1,
    is_default          BOOLEAN         NOT NULL DEFAULT FALSE,
    rollout_percent     TINYINT,
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exts_ei_code ON exts_extension_impl (extension_point_id, impl_code);
CREATE INDEX IF NOT EXISTS idx_exts_ei_tenant ON exts_extension_impl (tenant_id, extension_point_id);
CREATE INDEX IF NOT EXISTS idx_exts_ei_point ON exts_extension_impl (extension_point_id);

CREATE TABLE IF NOT EXISTS exts_plugin_version (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    plugin_id           BIGINT          NOT NULL,
    release_version     VARCHAR(50)     NOT NULL,
    file_path           VARCHAR(512)    NOT NULL,
    file_size           BIGINT,
    checksum            VARCHAR(64)     NOT NULL,
    is_active           BOOLEAN         NOT NULL DEFAULT FALSE,
    change_log          VARCHAR(500),
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exts_pv ON exts_plugin_version (plugin_id, release_version);
CREATE INDEX IF NOT EXISTS idx_exts_pv_tenant ON exts_plugin_version (tenant_id, plugin_id);
CREATE INDEX IF NOT EXISTS idx_exts_pv_plugin ON exts_plugin_version (plugin_id);

CREATE TABLE IF NOT EXISTS exts_plugin_execution_log (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    plugin_id           BIGINT          NOT NULL,
    extension_point_id  BIGINT          NOT NULL,
    execution_id        VARCHAR(64)     NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    input_data          CLOB,
    output_data         CLOB,
    error_message       CLOB,
    duration_ms         BIGINT,
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_exts_pel_exec ON exts_plugin_execution_log (execution_id);
CREATE INDEX IF NOT EXISTS idx_exts_pel_tenant ON exts_plugin_execution_log (tenant_id, plugin_id, created_at);
CREATE INDEX IF NOT EXISTS idx_exts_pel_plugin ON exts_plugin_execution_log (plugin_id);

-- Metadata SDK 嵌入式模式所需（Studio 不使用 EAV，但需满足 SqlRepository 引导）
CREATE TABLE IF NOT EXISTS column_allocation (
    id                  BIGINT          AUTO_INCREMENT PRIMARY KEY,
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
    created_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT,
    updated_at          TIMESTAMP       DEFAULT CURRENT_TIMESTAMP
);
