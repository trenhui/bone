-- ----------------------------
-- 清理已存在的表（按依赖顺序）
-- ----------------------------
DROP TABLE IF EXISTS column_allocation;
DROP TABLE IF EXISTS field_metadata;
DROP TABLE IF EXISTS ext_data_reserved;
DROP TABLE IF EXISTS ext_data_json;
DROP TABLE IF EXISTS ext_data_eav;

-- ----------------------------
-- 扩展列分配表
-- ----------------------------
CREATE TABLE column_allocation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL,
  app_code VARCHAR(64) NOT NULL,
  biz_identity_code VARCHAR(64) NOT NULL,
  entity_type VARCHAR(128) NOT NULL,
  data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('STRING','NUMBER','TEXT','DATE','BOOLEAN','INTEGER','JSON','XML','GEO')),
  column_name VARCHAR(64) NOT NULL,
  column_index INT NOT NULL,
  status VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE','IN_USE','RECYCLED')),
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  created_by BIGINT COMMENT '创建人',
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  updated_by BIGINT COMMENT '更新人',
  recycled_at TIMESTAMP(3) NULL COMMENT '回收时间',

  -- 唯一约束调整
  CONSTRAINT uq_allocation UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, data_type, column_index),
  CONSTRAINT uq_column_name UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, column_name)
);

-- 核心索引优化
CREATE INDEX idx_tenant_entity ON column_allocation (tenant_id, app_code, biz_identity_code, entity_type);
CREATE INDEX idx_status_index ON column_allocation (status, column_index);
CREATE INDEX idx_recycled_at ON column_allocation (recycled_at);

-- ----------------------------
-- 字段元数据表（核心参考表）
-- ----------------------------
CREATE TABLE field_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    tenant_id VARCHAR(50) NOT NULL COMMENT '租户ID',
    app_code VARCHAR(50) NOT NULL COMMENT '应用编码',
    biz_identity_code VARCHAR(50) NOT NULL COMMENT '业务身份编码',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型',
    name VARCHAR(255) NOT NULL COMMENT '字段名称',
    column_name VARCHAR(255) NOT NULL COMMENT '列名称',
    data_type VARCHAR(50) NOT NULL COMMENT '数据类型',
    is_primary_key TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为主键',
    is_nullable TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否可为空',
    default_value TEXT COMMENT '默认值',
    constraints VARCHAR(500) COMMENT '约束条件',
    is_virtual TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为虚拟字段',
    is_extension TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为扩展字段',
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标识',
    created_by BIGINT NULL COMMENT '创建者ID',
    updated_by BIGINT NULL COMMENT '更新者ID',
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    CONSTRAINT uk_field_metadata UNIQUE (entity_type, name),
    INDEX idx_field_data_type (data_type)
) ENGINE=InnoDB CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='字段元数据表，记录所有字段的结构化信息';

-- ----------------------------
-- 实体扩展数据表 (RESERVED_COLUMNS模式)
-- ----------------------------
CREATE TABLE ext_data_reserved (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '实体ID（主键）',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    app_code VARCHAR(50) NOT NULL COMMENT '所属应用编码',
    biz_identity_code VARCHAR(50) NOT NULL COMMENT '业务身份编码',
    entity_id BIGINT NOT NULL COMMENT '实体ID（关联业务实体，无外键约束）',
    entity_type VARCHAR(50) NOT NULL COMMENT '实体类型（如"用户"/"订单"等）',

    -- 文本类型扩展字段 (1-20)
    ext_str_01 VARCHAR(500) COMMENT '扩展文本字段01',
    ext_str_02 VARCHAR(500) COMMENT '扩展文本字段02',
    ext_str_03 VARCHAR(500) COMMENT '扩展文本字段03',
    ext_str_04 VARCHAR(500) COMMENT '扩展文本字段04',
    ext_str_05 VARCHAR(500) COMMENT '扩展文本字段05',
    ext_str_06 VARCHAR(500) COMMENT '扩展文本字段06',
    ext_str_07 VARCHAR(500) COMMENT '扩展文本字段07',
    ext_str_08 VARCHAR(500) COMMENT '扩展文本字段08',
    ext_str_09 VARCHAR(500) COMMENT '扩展文本字段09',
    ext_str_10 VARCHAR(500) COMMENT '扩展文本字段10',
    ext_str_11 VARCHAR(500) COMMENT '扩展文本字段11',
    ext_str_12 VARCHAR(500) COMMENT '扩展文本字段12',
    ext_str_13 VARCHAR(500) COMMENT '扩展文本字段13',
    ext_str_14 VARCHAR(500) COMMENT '扩展文本字段14',
    ext_str_15 VARCHAR(500) COMMENT '扩展文本字段15',
    ext_str_16 VARCHAR(500) COMMENT '扩展文本字段16',
    ext_str_17 VARCHAR(500) COMMENT '扩展文本字段17',
    ext_str_18 VARCHAR(500) COMMENT '扩展文本字段18',
    ext_str_19 VARCHAR(500) COMMENT '扩展文本字段19',
    ext_str_20 VARCHAR(500) COMMENT '扩展文本字段20',

    -- 长文本类型扩展字段 (1-5)
    ext_text_01 TEXT COMMENT '扩展长文本字段01',
    ext_text_02 TEXT COMMENT '扩展长文本字段02',
    ext_text_03 TEXT COMMENT '扩展长文本字段03',
    ext_text_04 TEXT COMMENT '扩展长文本字段04',
    ext_text_05 TEXT COMMENT '扩展长文本字段05',

    -- 数值类型扩展字段 (1-10)（高精度小数）
    ext_num_01 DECIMAL(28,4) COMMENT '扩展数值字段01（高精度）',
    ext_num_02 DECIMAL(28,4) COMMENT '扩展数值字段02（高精度）',
    ext_num_03 DECIMAL(28,4) COMMENT '扩展数值字段03（高精度）',
    ext_num_04 DECIMAL(28,4) COMMENT '扩展数值字段04（高精度）',
    ext_num_05 DECIMAL(28,4) COMMENT '扩展数值字段05（高精度）',
    ext_num_06 DECIMAL(28,4) COMMENT '扩展数值字段06（高精度）',
    ext_num_07 DECIMAL(28,4) COMMENT '扩展数值字段07（高精度）',
    ext_num_08 DECIMAL(28,4) COMMENT '扩展数值字段08（高精度）',
    ext_num_09 DECIMAL(28,4) COMMENT '扩展数值字段09（高精度）',
    ext_num_10 DECIMAL(28,4) COMMENT '扩展数值字段10（高精度）',

    -- 整数类型扩展字段 (1-10)（BIGINT兼容大整数）
    ext_int_01 BIGINT COMMENT '扩展整数字段01',
    ext_int_02 BIGINT COMMENT '扩展整数字段02',
    ext_int_03 BIGINT COMMENT '扩展整数字段03',
    ext_int_04 BIGINT COMMENT '扩展整数字段04',
    ext_int_05 BIGINT COMMENT '扩展整数字段05',
    ext_int_06 BIGINT COMMENT '扩展整数字段06',
    ext_int_07 BIGINT COMMENT '扩展整数字段07',
    ext_int_08 BIGINT COMMENT '扩展整数字段08',
    ext_int_09 BIGINT COMMENT '扩展整数字段09',
    ext_int_10 BIGINT COMMENT '扩展整数字段10',

    -- 日期类型扩展字段 (1-10)（微秒精度）
    ext_date_01 DATETIME(6) COMMENT '扩展日期字段01（微秒精度）',
    ext_date_02 DATETIME(6) COMMENT '扩展日期字段02（微秒精度）',
    ext_date_03 DATETIME(6) COMMENT '扩展日期字段03（微秒精度）',
    ext_date_04 DATETIME(6) COMMENT '扩展日期字段04（微秒精度）',
    ext_date_05 DATETIME(6) COMMENT '扩展日期字段05（微秒精度）',
    ext_date_06 DATETIME(6) COMMENT '扩展日期字段06（微秒精度）',
    ext_date_07 DATETIME(6) COMMENT '扩展日期字段07（微秒精度）',
    ext_date_08 DATETIME(6) COMMENT '扩展日期字段08（微秒精度）',
    ext_date_09 DATETIME(6) COMMENT '扩展日期字段09（微秒精度）',
    ext_date_10 DATETIME(6) COMMENT '扩展日期字段10（微秒精度）',

    -- 布尔类型扩展字段 (1-5)（TINYINT(1)表示0/1）
    ext_boolean_01 TINYINT(1) DEFAULT 0 COMMENT '扩展布尔字段01（0否，1是）',
    ext_boolean_02 TINYINT(1) DEFAULT 0 COMMENT '扩展布尔字段02（0否，1是）',
    ext_boolean_03 TINYINT(1) DEFAULT 0 COMMENT '扩展布尔字段03（0否，1是）',
    ext_boolean_04 TINYINT(1) DEFAULT 0 COMMENT '扩展布尔字段04（0否，1是）',
    ext_boolean_05 TINYINT(1) DEFAULT 0 COMMENT '扩展布尔字段05（0否，1是）',

    -- 特殊类型扩展字段
    ext_geo_01 GEOMETRY COMMENT '地理空间数据字段（支持点/线/面等）',
    ext_json_01 JSON COMMENT 'JSON结构化数据字段（灵活扩展属性）',
    ext_xml_01 TEXT COMMENT 'XML结构化数据字段（传统格式兼容）',

    -- 逻辑删除与审计字段
    deleted TINYINT(1) DEFAULT 0 COMMENT '逻辑删除标识（0未删除，1已删除）',
    created_by BIGINT NULL COMMENT '创建者ID（关联sys_user.id，无外键）',
    updated_by BIGINT NULL COMMENT '更新者ID（关联sys_user.id，无外键）',
    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（精确到微秒）',
    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间（自动刷新）',

    -- 唯一索引：强制租户+应用+业务身份+实体类型+实体ID 唯一
    CONSTRAINT uniq_ext_data_biz_uniq
        UNIQUE (tenant_id, app_code, biz_identity_code, entity_type, entity_id)
) ENGINE=InnoDB
  CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci
  COMMENT='实体扩展数据表（支持动态字段扩展，核心业务组合唯一，无外键约束）';

CREATE TABLE ext_data_json (
    entity_type VARCHAR(50) COMMENT '实体类型',
    entity_id BIGINT COMMENT '实体ID',
    data_json JSON COMMENT 'JSON格式扩展数据',
    version BIGINT COMMENT '版本号，用于乐观锁控制',
    deleted BOOLEAN DEFAULT FALSE COMMENT '删除标志，true表示已删除',
    PRIMARY KEY (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储实体扩展数据的JSON格式表';

CREATE TABLE ext_data_eav (
    entity_type VARCHAR(50) COMMENT '实体类型',
    entity_id BIGINT COMMENT '实体ID',
    attr_key VARCHAR(100) COMMENT '属性键',
    attr_value TEXT COMMENT '属性值',
    version BIGINT COMMENT '版本号，用于乐观锁控制',
    deleted BOOLEAN DEFAULT FALSE COMMENT '删除标志，true表示已删除',
    PRIMARY KEY (entity_type, entity_id, attr_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存储实体扩展数据的EAV(属性-值)格式表';
