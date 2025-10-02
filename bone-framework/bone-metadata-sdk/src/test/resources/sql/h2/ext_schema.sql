-- 建表语句
CREATE TABLE ext_data_reserved (
    -- 核心标识字段
    id BIGINT   AUTO_INCREMENT PRIMARY KEY,
    tenant_id   BIGINT   NOT NULL,
    app_code    VARCHAR(50)   NOT NULL,
    biz_identity_code    VARCHAR(50)   NOT NULL,
    entity_id         BIGINT NOT NULL,
    entity_type VARCHAR(50) NOT NULL,

    -- 预留文本字段 (全量20列)
    ext_str_01 VARCHAR(500),
    ext_str_02 VARCHAR(500),
    ext_str_03 VARCHAR(500),
    ext_str_04 VARCHAR(500),
    ext_str_05 VARCHAR(500),
    ext_str_06 VARCHAR(500),
    ext_str_07 VARCHAR(500),
    ext_str_08 VARCHAR(500),
    ext_str_09 VARCHAR(500),
    ext_str_10 VARCHAR(500),
    ext_str_11 VARCHAR(500),
    ext_str_12 VARCHAR(500),
    ext_str_13 VARCHAR(500),
    ext_str_14 VARCHAR(500),
    ext_str_15 VARCHAR(500),
    ext_str_16 VARCHAR(500),
    ext_str_17 VARCHAR(500),
    ext_str_18 VARCHAR(500),
    ext_str_19 VARCHAR(500),
    ext_str_20 VARCHAR(500),

    ext_text_01 VARCHAR(8000),
    ext_text_02 VARCHAR(8000),
    ext_text_03 VARCHAR(8000),
    ext_text_04 VARCHAR(8000),
    ext_text_05 VARCHAR(8000),

    -- 高精度数值字段
    ext_num_01 DECIMAL(28,4),
    ext_num_02 DECIMAL(28,4),
    ext_num_03 DECIMAL(28,4),
    ext_num_04 DECIMAL(28,4),
    ext_num_05 DECIMAL(28,4),
    ext_num_06 DECIMAL(28,4),
    ext_num_07 DECIMAL(28,4),
    ext_num_08 DECIMAL(28,4),
    ext_num_09 DECIMAL(28,4),
    ext_num_10 DECIMAL(28,4),

    -- 整数字段
    ext_int_01 BIGINT,
    ext_int_02 BIGINT,
    ext_int_03 BIGINT,
    ext_int_04 BIGINT,
    ext_int_05 BIGINT,
    ext_int_06 BIGINT,
    ext_int_07 BIGINT,
    ext_int_08 BIGINT,
    ext_int_09 BIGINT,
    ext_int_10 BIGINT,

    -- 日期字段
    ext_date_01 DATETIME,
    ext_date_02 DATETIME,
    ext_date_03 DATETIME,
    ext_date_04 DATETIME,
    ext_date_05 DATETIME,
    ext_date_06 DATETIME,
    ext_date_07 DATETIME,
    ext_date_08 DATETIME,
    ext_date_09 DATETIME,
    ext_date_10 DATETIME,

   -- 布尔字段 (5列)
    ext_boolean_01 BOOLEAN,
    ext_boolean_02 BOOLEAN,
    ext_boolean_03 BOOLEAN,
    ext_boolean_04 BOOLEAN,
    ext_boolean_05 BOOLEAN,

    -- 复合字段
    ext_geo_01 GEOMETRY,          -- H2 2.0+ 支持
    ext_json_01 VARCHAR(16777216),-- JSON存储字段
    ext_xml_01 VARCHAR(16384),

    -- 系统字段
    deleted BOOLEAN DEFAULT FALSE,
    create_by BIGINT  NULL,
    update_by BIGINT  NULL,
    create_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    update_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
);

-- 表注释
COMMENT ON TABLE ext_data_reserved IS '实体扩展数据表 (RESERVED_COLUMNS模式用于动态字段)';

-- 列注释 (示例部分关键字段，其他字段类似)
COMMENT ON COLUMN ext_data_reserved.id IS '实体ID';
COMMENT ON COLUMN ext_data_reserved.entity_type IS '实体类型 (例如 Account, Order)';
COMMENT ON COLUMN ext_data_reserved.app_code IS '所属应用编码';
COMMENT ON COLUMN ext_data_reserved.ext_str_01 IS '扩展文本字段 01';
COMMENT ON COLUMN ext_data_reserved.ext_num_01 IS '扩展数值字段 01 (高精度)';
COMMENT ON COLUMN ext_data_reserved.ext_int_01 IS '扩展整数字段 01';
COMMENT ON COLUMN ext_data_reserved.ext_date_01 IS '扩展日期字段 01 (微秒精度)';
COMMENT ON COLUMN ext_data_reserved.ext_geo_01 IS '地理空间数据字段';
COMMENT ON COLUMN ext_data_reserved.ext_json_01 IS 'JSON结构化数据字段';
COMMENT ON COLUMN ext_data_reserved.ext_xml_01 IS 'XML结构化数据字段';

-- 索引创建
-- 扩展表增加业务维度索引
CREATE INDEX idx_biz_dimension ON ext_data_reserved(tenant_id, app_code, biz_identity_code, entity_type, entity_id);


-- 字段元数据表 (H2 语法)
-- 字段元数据表 (H2 语法)
CREATE TABLE field_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id      VARCHAR(50)   NOT NULL,
    app_code       VARCHAR(50)   NOT NULL,
    biz_identity_code    VARCHAR(50)   NOT NULL,
    entity_type    VARCHAR(50)   NOT NULL,
    name           VARCHAR(255) NOT NULL,
    column_name    VARCHAR(255) NOT NULL,
    data_type      VARCHAR(50)  NOT NULL,
    is_primary_key BOOLEAN      NOT NULL DEFAULT FALSE,
    is_nullable    BOOLEAN      NOT NULL DEFAULT TRUE,
    default_value  CLOB,
    constraints    VARCHAR(500),
    is_virtual     BOOLEAN      NOT NULL DEFAULT FALSE,
    is_extension   BOOLEAN      NOT NULL DEFAULT FALSE,

    -- 系统字段
    deleted BOOLEAN DEFAULT FALSE,
    create_by BIGINT  NULL,
    update_by BIGINT  NULL,
    create_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
    update_time TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),

    -- 关键修复点：添加联合唯一约束
    CONSTRAINT uk_entity_field UNIQUE (entity_type, name)
);
CREATE INDEX idx_data_type ON field_metadata(data_type);

COMMENT ON TABLE field_metadata IS '字段元数据表，记录所有字段的结构化信息';
COMMENT ON COLUMN field_metadata.entity_type IS '所属实体类型 (例如 Account、Order)';
COMMENT ON COLUMN field_metadata.name IS '业务字段名称 (唯一标识)';
COMMENT ON COLUMN field_metadata.column_name IS '数据库列名 (实际存储的列名)';
COMMENT ON COLUMN field_metadata.data_type IS '数据类型 (STRING/INTEGER/NUMBER/DATE/JSON)';
COMMENT ON COLUMN field_metadata.is_primary_key IS '是否主键';
COMMENT ON COLUMN field_metadata.is_nullable IS '是否可空';
COMMENT ON COLUMN field_metadata.default_value IS '默认值 (存储为字符串形式)';
COMMENT ON COLUMN field_metadata.constraints IS '约束条件 (如 UNIQUE、FOREIGN KEY)';
COMMENT ON COLUMN field_metadata.is_virtual IS '是否为虚拟字段';
COMMENT ON COLUMN field_metadata.is_extension IS '是否为扩展字段';



CREATE TABLE column_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  app_code VARCHAR(64) NOT NULL,
  biz_identity_code VARCHAR(64) NOT NULL,
  entity_type VARCHAR(128) NOT NULL,
  data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('STRING','NUMBER','DATE','BOOLEAN','INTEGER','JSON')),
  column_name VARCHAR(64) NOT NULL,
  column_index INT NOT NULL,
  status VARCHAR(64) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  created_by BIGINT COMMENT '创建人',
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT COMMENT '更新人',
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),


  -- 唯一约束
  CONSTRAINT uq_allocation UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, data_type, column_index),
  CONSTRAINT uq_column_name UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, column_name)
);

-- 单独创建索引
CREATE INDEX idx_partition ON column_allocation (tenant_id, app_code, biz_identity_code, entity_type, data_type);

-- 添加表注释和列注释
COMMENT ON TABLE column_allocation IS 'Extension column allocation table';
COMMENT ON COLUMN column_allocation.id IS 'Primary key ID';
COMMENT ON COLUMN column_allocation.tenant_id IS 'Tenant ID';
COMMENT ON COLUMN column_allocation.app_code IS 'Application code';
COMMENT ON COLUMN column_allocation.biz_identity_code IS 'Business identity code';
COMMENT ON COLUMN column_allocation.entity_type IS 'Entity type';
COMMENT ON COLUMN column_allocation.data_type IS 'Data type';
COMMENT ON COLUMN column_allocation.column_name IS 'Column name';
COMMENT ON COLUMN column_allocation.column_index IS 'Column index';
COMMENT ON COLUMN column_allocation.version IS 'Optimistic lock version';
COMMENT ON COLUMN column_allocation.created_at IS 'Creation timestamp';
COMMENT ON COLUMN column_allocation.updated_at IS 'Update timestamp';


-- 字段权限表 (H2 语法)
CREATE TABLE field_permission (
    role_code    VARCHAR(50)  NOT NULL,
    entity_type  VARCHAR(50)  NOT NULL,
    field_name   VARCHAR(255) NOT NULL,
    can_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    can_write    BOOLEAN      NOT NULL DEFAULT FALSE,

    PRIMARY KEY (role_code, entity_type, field_name),
    FOREIGN KEY (entity_type, field_name) REFERENCES field_metadata(entity_type, name)
);

COMMENT ON TABLE field_permission IS '字段权限控制表';
COMMENT ON COLUMN field_permission.role_code IS '角色代码';
COMMENT ON COLUMN field_permission.entity_type IS '实体类型';
COMMENT ON COLUMN field_permission.field_name IS '字段名称';
COMMENT ON COLUMN field_permission.can_read IS '读权限';
COMMENT ON COLUMN field_permission.can_write IS '写权限';

CREATE TABLE ext_data_json (
    entity_type VARCHAR(50),
    entity_id BIGINT,
    data_json JSON,
    version BIGINT,
    deleted BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE ext_data_eav (
    entity_type VARCHAR(50),
    entity_id BIGINT,
    attr_key VARCHAR(100),
    attr_value TEXT,
    version BIGINT,
    deleted BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (entity_type, entity_id, attr_key)
);