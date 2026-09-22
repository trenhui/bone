CREATE TABLE IF NOT EXISTS md_field (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL DEFAULT 0,
    master_data_entity_id   BIGINT       NOT NULL,
    name                    VARCHAR(200) NOT NULL,
    code                    VARCHAR(100),
    type                    VARCHAR(50),
    length                  INT,
    required                TINYINT      DEFAULT 0,
    default_value           VARCHAR(500),
    description             VARCHAR(500),
    sort_order              INT          DEFAULT 0,
    created_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT,
    updated_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT,
    deleted                 BOOLEAN      DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS md_entity (
    id              BIGINT       NOT NULL PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL DEFAULT 0,
    meta_entity_id  BIGINT,
    name            VARCHAR(200) NOT NULL,
    description     CLOB,
    category        VARCHAR(100),
    status          VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_md_entity_meta ON md_entity (meta_entity_id);

CREATE TABLE IF NOT EXISTS meta_entity (
    id           BIGINT       NOT NULL PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL DEFAULT 0,
    name         VARCHAR(200) NOT NULL,
    code         VARCHAR(200) NOT NULL,
    display_name VARCHAR(200),
    status       TINYINT      NOT NULL DEFAULT 0,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

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
