-- G1 收敛：聚合已切换到 mdm_*（bone-init.sql §8 为 DDL 真源）；测试库同步改名并补治理列
CREATE TABLE IF NOT EXISTS mdm_field (
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

CREATE TABLE IF NOT EXISTS mdm_entity (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 0,
    meta_entity_id      BIGINT,
    entity_code         VARCHAR(200),
    entity_name         VARCHAR(200) NOT NULL,
    description         CLOB,
    category            VARCHAR(100),
    domain_code         VARCHAR(64),
    template_id         BIGINT,
    template_version    VARCHAR(32),
    owning_app_id       BIGINT,
    governance_tier     VARCHAR(16)  DEFAULT 'L1',
    is_versioning       BOOLEAN      DEFAULT FALSE,
    workflow_enabled    BOOLEAN      DEFAULT FALSE,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mdm_entity_meta ON mdm_entity (tenant_id, meta_entity_id);

-- 域模板目录（平台全局，非租户作用域；实体不映射 tenant_id，列保留 DEFAULT 0——bone-init.sql §8 为 DDL 真源）
CREATE TABLE IF NOT EXISTS mdm_domain_template (
    id                      BIGINT       NOT NULL PRIMARY KEY,
    tenant_id               BIGINT       NOT NULL DEFAULT 0,
    domain_code             VARCHAR(64)  NOT NULL,
    domain_name             VARCHAR(200) NOT NULL,
    description             CLOB,
    current_version         VARCHAR(32)  NOT NULL DEFAULT '1.0.0',
    default_governance_tier VARCHAR(16)  NOT NULL DEFAULT 'L1',
    field_schema            CLOB,
    rule_schema             CLOB,
    category_schema         CLOB,
    status                  VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    created_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    created_by              BIGINT,
    updated_at              TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_by              BIGINT,
    deleted                 BOOLEAN      DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS mdm_template_version (
    id               BIGINT       NOT NULL PRIMARY KEY,
    tenant_id        BIGINT       NOT NULL DEFAULT 0,
    template_id      BIGINT       NOT NULL,
    version_number   VARCHAR(32)  NOT NULL,
    change_log       CLOB,
    field_schema     CLOB,
    rule_schema      CLOB,
    category_schema  CLOB,
    created_at       TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    deleted          BOOLEAN      DEFAULT FALSE
);

-- 参考数据 overlay（2026-09-26 裁决 §8 约束6）：平台目录 + 平台值（非租户作用域）+ 租户私有值
CREATE TABLE IF NOT EXISTS mdm_reference_set (
    id                BIGINT       NOT NULL PRIMARY KEY,
    tenant_id         BIGINT       NOT NULL DEFAULT 0,
    set_code          VARCHAR(64)  NOT NULL,
    set_name          VARCHAR(200) NOT NULL,
    external_standard VARCHAR(64),
    description       CLOB,
    status            VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED',
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted           BOOLEAN      DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS mdm_reference_value (
    id             BIGINT        NOT NULL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 0,
    set_id         BIGINT        NOT NULL,
    value_code     VARCHAR(100)  NOT NULL,
    value_name     VARCHAR(200)  NOT NULL,
    external_code  VARCHAR(100),
    sort_order     INT           NOT NULL DEFAULT 0,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN       DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS mdm_reference_value_tenant (
    id             BIGINT        NOT NULL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL,
    set_id         BIGINT        NOT NULL,
    value_code     VARCHAR(100)  NOT NULL,
    value_name     VARCHAR(200)  NOT NULL,
    external_code  VARCHAR(100),
    sort_order     INT           NOT NULL DEFAULT 0,
    enabled        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN       DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mdm_refval_tenant
    ON mdm_reference_value_tenant (tenant_id, set_id, value_code);

CREATE TABLE IF NOT EXISTS mdm_record (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 0,
    mdm_entity_id       BIGINT       NOT NULL,
    record_code         VARCHAR(200),
    display_name        VARCHAR(500),
    current_data        CLOB         NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    version_number      INT          DEFAULT 1,
    effective_from      TIMESTAMP,
    effective_to        TIMESTAMP,
    is_current          BOOLEAN      DEFAULT FALSE,
    parent_record_id    BIGINT,
    submitted_by        BIGINT,
    publish_time        TIMESTAMP,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mdm_record_version (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    record_id           BIGINT       NOT NULL,
    version_number      INT          NOT NULL,
    data                CLOB         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    change_description  CLOB,
    approved_by         BIGINT,
    approved_at         TIMESTAMP,
    created_by          BIGINT,
    created_at          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

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
