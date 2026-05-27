-- 订单主表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    tenant_id BIGINT COMMENT '租户ID',
    customer_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单明细表
CREATE TABLE IF NOT EXISTS t_order_item (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    order_id BIGINT NOT NULL COMMENT '关联 t_order.id',
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 集成事件 Outbox（蓝图示范）
CREATE TABLE IF NOT EXISTS bp_outbox (
    id BIGINT PRIMARY KEY COMMENT 'Snowflake ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    event_id VARCHAR(36) NOT NULL COMMENT '事件 UUID',
    event_type VARCHAR(80) NOT NULL COMMENT '事件类型',
    topic VARCHAR(200) NOT NULL COMMENT 'MQ Topic',
    partition_key VARCHAR(100) NOT NULL COMMENT '分区键',
    envelope_json JSON NOT NULL COMMENT '消息信封 JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    sent_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_bp_outbox_event_id (event_id),
    KEY idx_bp_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='蓝图集成事件 Outbox';
