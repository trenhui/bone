-- 创建column_allocation表，用于bone-metadata-sdk的扩展字段管理
CREATE TABLE IF NOT EXISTS column_allocation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_code VARCHAR(128) NOT NULL,
    biz_identity_code VARCHAR(128) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    column_index INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NOT NULL,
    recycled_at TIMESTAMP NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_tenant_app_biz_entity_type_index (tenant_id, app_code, biz_identity_code, entity_type, data_type, column_index),
    INDEX idx_status (status)
);

-- 创建field_metadata表，用于存储字段元数据
CREATE TABLE IF NOT EXISTS field_metadata (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    app_code VARCHAR(128) NOT NULL,
    biz_identity_code VARCHAR(128) NOT NULL,
    entity_type VARCHAR(128) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_label VARCHAR(256) NOT NULL,
    data_type VARCHAR(32) NOT NULL,
    column_name VARCHAR(128) NOT NULL,
    column_index INT NOT NULL,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    default_value VARCHAR(1024) NULL,
    validation_rule VARCHAR(1024) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_by VARCHAR(64) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_field_identity (tenant_id, app_code, biz_identity_code, entity_type, field_name),
    INDEX idx_entity_type (tenant_id, app_code, biz_identity_code, entity_type)
);

-- 创建供应商表
CREATE TABLE IF NOT EXISTS procurement_supplier (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '供应商编码',
    name VARCHAR(200) NOT NULL COMMENT '供应商名称',
    phone_number VARCHAR(50) NOT NULL COMMENT '联系电话',
    contact_person VARCHAR(100) NOT NULL COMMENT '联系人',
    email VARCHAR(200) NULL COMMENT '电子邮箱',
    address VARCHAR(500) NULL COMMENT '地址',
    business_license VARCHAR(200) NULL COMMENT '营业执照号码',
    register_date DATE NULL COMMENT '注册日期',
    supplier_level VARCHAR(20) NULL COMMENT '供应商等级',
    credit_score INT NULL COMMENT '信用评分',
    cooperation_status VARCHAR(50) NULL COMMENT '合作状态',
    last_cooperation_date DATE NULL COMMENT '最后合作日期',
    total_order_amount DECIMAL(18,2) NULL COMMENT '总订单金额',
    order_count INT NULL COMMENT '订单数量',
    average_delivery_rate DECIMAL(5,2) NULL COMMENT '平均交付率',
    average_quality_rate DECIMAL(5,2) NULL COMMENT '平均质量率',
    complaint_count INT NULL COMMENT '投诉次数',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_name (name),
    INDEX idx_code (code),
    INDEX idx_cooperation_status (cooperation_status),
    INDEX idx_credit_score (credit_score)
);