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

-- ============================================================
-- 骨-system 业务表（与 bone-init.sql 对齐，H2 兼容语法）
-- 缺失会导致 @SpringBootTest 上下文启动失败
-- （如 TaskSchedulerRegistry 在 ApplicationReadyEvent 查 sys_schedule_task）。
-- ============================================================

CREATE TABLE IF NOT EXISTS sys_log (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    level       VARCHAR(20)  NOT NULL,
    service     VARCHAR(100) NOT NULL,
    content     CLOB         NOT NULL,
    trace_id    VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_dict (
    id         BIGINT        NOT NULL PRIMARY KEY,
    tenant_id  BIGINT        NOT NULL DEFAULT 0,
    type       VARCHAR(50)   NOT NULL,
    type_name  VARCHAR(100),
    code       VARCHAR(100),
    label      VARCHAR(100),
    `value`    VARCHAR(255),
    sort       INT           DEFAULT 0,
    status     INT           NOT NULL DEFAULT 1,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted    TINYINT       NOT NULL DEFAULT 0,
    version    INT           NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id            BIGINT        NOT NULL PRIMARY KEY,
    tenant_id     BIGINT        NOT NULL DEFAULT 0,
    code          VARCHAR(64)   NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    category      VARCHAR(20)   NOT NULL DEFAULT 'LIST',
    module_code   VARCHAR(64),
    enum_class    VARCHAR(255),
    max_depth     INT           NOT NULL DEFAULT 0,
    value_type    VARCHAR(20)   NOT NULL DEFAULT 'STRING',
    value_regex   VARCHAR(255),
    code_segments VARCHAR(64),
    description   VARCHAR(255),
    builtin       TINYINT       NOT NULL DEFAULT 0,
    editable      TINYINT       NOT NULL DEFAULT 1,
    sort          INT           NOT NULL DEFAULT 0,
    status        INT           NOT NULL DEFAULT 1,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT       NOT NULL DEFAULT 0,
    version       INT           NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_dict_item (
    id             BIGINT        NOT NULL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 0,
    type_code      VARCHAR(64)   NOT NULL,
    code           VARCHAR(100)  NOT NULL,
    label          VARCHAR(100)  NOT NULL,
    `value`        VARCHAR(255),
    enum_name      VARCHAR(100),
    tag_type       VARCHAR(20)   NOT NULL DEFAULT 'default',
    i18n_key       VARCHAR(128),
    external_code  VARCHAR(100),
    effective_from TIMESTAMP,
    effective_to   TIMESTAMP,
    is_default     TINYINT       NOT NULL DEFAULT 0,
    sort           INT           NOT NULL DEFAULT 0,
    status         INT           NOT NULL DEFAULT 1,
    description    VARCHAR(255),
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    version        INT           NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_dict_hierarchy (
    id             BIGINT        NOT NULL PRIMARY KEY,
    tenant_id      BIGINT        NOT NULL DEFAULT 0,
    type_code      VARCHAR(64)   NOT NULL,
    hierarchy_code VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
    code           VARCHAR(100)  NOT NULL,
    parent_code    VARCHAR(100),
    path           VARCHAR(512)  NOT NULL,
    level          INT           NOT NULL DEFAULT 1,
    sort           INT           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        TINYINT       NOT NULL DEFAULT 0,
    version        INT           NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_dict_item_text (
    id          BIGINT        NOT NULL PRIMARY KEY,
    tenant_id   BIGINT        NOT NULL DEFAULT 0,
    type_code   VARCHAR(64)   NOT NULL,
    code        VARCHAR(100)  NOT NULL,
    language    VARCHAR(16)   NOT NULL,
    label       VARCHAR(100)  NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    version     INT           NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_schedule_task (
    id          BIGINT       NOT NULL PRIMARY KEY,
    tenant_id   BIGINT       NOT NULL DEFAULT 0,
    name        VARCHAR(100) NOT NULL,
    cron        VARCHAR(100),
    handler     VARCHAR(255),
    status      VARCHAR(20)  NOT NULL DEFAULT 'DISABLED',
    last_run_at TIMESTAMP,
    next_run_at TIMESTAMP,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    version     INT          NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_alert_rule (
    id                    BIGINT        NOT NULL PRIMARY KEY,
    tenant_id             BIGINT        NOT NULL DEFAULT 0,
    name                  VARCHAR(100)  NOT NULL,
    description           VARCHAR(255),
    metric_name           VARCHAR(100)  NOT NULL,
    threshold_value       DECIMAL(20,4) NOT NULL,
    alert_level           VARCHAR(20)   NOT NULL,
    notification_channels CLOB,
    enabled               TINYINT       NOT NULL DEFAULT 1,
    created_by            BIGINT,
    updated_by            BIGINT,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted               TINYINT       NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_alert_event (
    id              BIGINT        NOT NULL PRIMARY KEY,
    tenant_id       BIGINT        NOT NULL DEFAULT 0,
    rule_id         BIGINT        NOT NULL,
    rule_name       VARCHAR(100),
    alert_level     VARCHAR(20)   NOT NULL,
    metric_name     VARCHAR(100)  NOT NULL,
    current_value   DECIMAL(20,4) NOT NULL,
    threshold_value DECIMAL(20,4) NOT NULL,
    message         VARCHAR(500)  NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'TRIGGERED',
    resolved_at     TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
