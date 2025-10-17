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