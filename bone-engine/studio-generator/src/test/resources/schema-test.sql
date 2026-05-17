DROP TABLE IF EXISTS gen_code_template;
DROP TABLE IF EXISTS gen_data_source;

CREATE TABLE gen_data_source (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 0,
    name                VARCHAR(100) NOT NULL,
    db_type             VARCHAR(32)  NOT NULL,
    host                VARCHAR(255) NOT NULL,
    port                INT          NOT NULL,
    db_name             VARCHAR(128) NOT NULL,
    username            VARCHAR(128) NOT NULL,
    password_encrypted  VARCHAR(512) NOT NULL,
    params              VARCHAR(500),
    is_enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    last_test_at        TIMESTAMP,
    last_test_result    VARCHAR(20),
    last_test_message   VARCHAR(500),
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0
);

CREATE TABLE gen_code_template (
    id                  BIGINT       NOT NULL PRIMARY KEY,
    tenant_id           BIGINT       NOT NULL DEFAULT 0,
    name                VARCHAR(200) NOT NULL,
    code                VARCHAR(200) NOT NULL,
    description         CLOB,
    type                VARCHAR(50)  NOT NULL,
    language            VARCHAR(20)  NOT NULL DEFAULT 'java',
    engine              VARCHAR(20)  NOT NULL DEFAULT 'FREEMARKER',
    template_version    VARCHAR(50)  NOT NULL DEFAULT '1.0.0',
    content             CLOB         NOT NULL,
    sample_output       CLOB,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at        TIMESTAMP,
    created_by          BIGINT,
    updated_by          BIGINT,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    version             INT          NOT NULL DEFAULT 0
);
