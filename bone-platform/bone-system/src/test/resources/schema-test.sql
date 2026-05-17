CREATE TABLE IF NOT EXISTS sys_config (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    config_key      VARCHAR(100) NOT NULL,
    config_value    CLOB         NOT NULL,
    description     VARCHAR(255),
    config_type     VARCHAR(20)  NOT NULL,
    encrypted       TINYINT      NOT NULL DEFAULT 0,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    version         INT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_config_key ON sys_config (tenant_id, config_key);

CREATE TABLE IF NOT EXISTS field_metadata (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         VARCHAR(50)  NOT NULL,
    app_code          VARCHAR(50)  NOT NULL,
    biz_identity_code VARCHAR(50)  NOT NULL,
    entity_type       VARCHAR(50)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    column_name       VARCHAR(255) NOT NULL,
    data_type         VARCHAR(50)  NOT NULL,
    is_primary_key    BOOLEAN      NOT NULL DEFAULT FALSE,
    is_nullable       BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value     CLOB,
    constraints       VARCHAR(500),
    is_virtual        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_extension      BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted           BOOLEAN      DEFAULT FALSE,
    created_by        BIGINT,
    updated_by        BIGINT,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS column_allocation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL,
    app_code          VARCHAR(64)  NOT NULL,
    biz_identity_code VARCHAR(64)  NOT NULL,
    entity_type       VARCHAR(128) NOT NULL,
    data_type         VARCHAR(20)  NOT NULL,
    column_name       VARCHAR(64)  NOT NULL,
    column_index      INT          NOT NULL,
    status            VARCHAR(64)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    created_by        BIGINT,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);
