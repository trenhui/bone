drop table extensible_object;

CREATE TABLE extensible_object (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_id BIGINT NOT NULL,
    property_name VARCHAR(255) NOT NULL,
    property_value TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by BIGINT,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    version INTEGER DEFAULT 0,
    tenant_id BIGINT,
    PRIMARY KEY (id),
    UNIQUE KEY (object_id, property_name)
);

drop table extend_object;
CREATE TABLE extend_object (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    object_id BIGINT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by BIGINT,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    -- 字符串类型预留字段
    extra_str_1 VARCHAR(255),
    extra_str_2 VARCHAR(255),
    extra_str_3 VARCHAR(255),
    extra_str_4 VARCHAR(255),
    extra_str_5 VARCHAR(255),
    extra_str_6 VARCHAR(255),
    extra_str_7 VARCHAR(255),
    extra_str_8 VARCHAR(255),
    extra_str_9 VARCHAR(255),
    extra_str_10 VARCHAR(255),
    extra_str_11 VARCHAR(255),
    extra_str_12 VARCHAR(255),
    extra_str_13 VARCHAR(255),
    extra_str_14 VARCHAR(255),
    extra_str_15 VARCHAR(255),
    extra_str_16 VARCHAR(255),
    extra_str_17 VARCHAR(255),
    extra_str_18 VARCHAR(255),
    extra_str_19 VARCHAR(255),
    extra_str_20 VARCHAR(255),
    -- BIGINT类型预留字段
    extra_bigint_1 BIGINT,
    extra_bigint_2 BIGINT,
    extra_bigint_3 BIGINT,
    extra_bigint_4 BIGINT,
    extra_bigint_5 BIGINT,
    -- 布尔类型预留字段
    extra_bool_1 BOOLEAN,
    extra_bool_2 BOOLEAN,
    extra_bool_3 BOOLEAN,
    extra_bool_4 BOOLEAN,
    extra_bool_5 BOOLEAN,
    -- 日期类型预留字段
    extra_date_1 DATE,
    extra_date_2 DATE,
    extra_date_3 DATE,
    extra_date_4 DATE,
    extra_date_5 DATE,
    -- 金额类型预留字段
    extra_amount_1 DECIMAL(19,4),
    extra_amount_2 DECIMAL(19,4),
    extra_amount_3 DECIMAL(19,4),
    extra_amount_4 DECIMAL(19,4),
    extra_amount_5 DECIMAL(19,4)
);