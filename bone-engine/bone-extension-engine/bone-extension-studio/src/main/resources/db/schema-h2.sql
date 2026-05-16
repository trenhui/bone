-- Studio 扩展点 / 扩展实现（H2 联调；生产 MySQL 见 schema-mysql.sql）
CREATE TABLE IF NOT EXISTS ext_studio_extension_point (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    tenant_id           BIGINT          NOT NULL DEFAULT 0,
    point_name          VARCHAR(255)    NOT NULL,
    point_code          VARCHAR(200)    NOT NULL,
    description         CLOB,
    interface_name      VARCHAR(500)    NOT NULL,
    biz_domain          VARCHAR(100),
    category            VARCHAR(100),
    status              VARCHAR(20)     NOT NULL DEFAULT 'ENABLED',
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           BIGINT,
    update_by           BIGINT,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ext_studio_ep_code ON ext_studio_extension_point (tenant_id, point_code);
CREATE INDEX IF NOT EXISTS idx_ext_studio_ep_iface ON ext_studio_extension_point (interface_name);

CREATE TABLE IF NOT EXISTS ext_studio_extension_impl (
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
    create_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           BIGINT,
    update_by           BIGINT,
    deleted             BOOLEAN         NOT NULL DEFAULT FALSE,
    version             INT             NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ext_studio_ei_code ON ext_studio_extension_impl (extension_point_id, impl_code);
CREATE INDEX IF NOT EXISTS idx_ext_studio_ei_point ON ext_studio_extension_impl (extension_point_id);

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
