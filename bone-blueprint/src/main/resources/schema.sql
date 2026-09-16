-- 订单主表
CREATE TABLE IF NOT EXISTS t_order (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(64) COMMENT '业务身份编码',
    customer_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（E-9.6）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_customer_id (customer_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单明细表
CREATE TABLE IF NOT EXISTS t_order_item (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    biz_identity_code VARCHAR(64) COMMENT '业务身份编码',
    order_id BIGINT NOT NULL COMMENT '关联 t_order.id（租户隔离经父聚合 t_order.tenant_id 间接保证，DDD 子实体标准做法）',
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    subtotal DECIMAL(10,2) NOT NULL,
    created_by BIGINT,
    updated_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 支付单表（支付限界上下文，样板示范）
CREATE TABLE IF NOT EXISTS bp_payment (
    id BIGINT PRIMARY KEY COMMENT '雪花算法生成的全局唯一ID',
    tenant_id BIGINT COMMENT '租户ID',
    biz_identity_code VARCHAR(64) COMMENT '业务身份编码',
    order_id BIGINT NOT NULL COMMENT '关联 t_order.id',
    customer_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    channel VARCHAR(30) NOT NULL COMMENT '支付渠道：SIMULATED/WECHAT/ALIPAY',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/PAYING/SUCCESS/FAILED/CLOSED',
    channel_trade_no VARCHAR(64) DEFAULT NULL COMMENT '渠道流水号（幂等去重键）',
    pay_url VARCHAR(500) DEFAULT NULL COMMENT '支付链接',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（E-9.6）',
    paid_at DATETIME(3) DEFAULT NULL,
    refunded_at DATETIME(3) DEFAULT NULL COMMENT '退款时间',
    refund_amount DECIMAL(10,2) DEFAULT NULL COMMENT '退款金额',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_payment_order_id (order_id),
    INDEX idx_payment_status (status),
    INDEX idx_payment_order_channel (order_id, channel),
    -- E-9.6.2 幂等键兜底：channel_trade_no 为渠道回填的流水号（幂等去重键），回填后须全局唯一。
    -- MySQL 中 NULL 不计入唯一约束，故 PENDING（未回调）行的多个 NULL 不冲突，回调回填后强制唯一，
    -- 防渠道重复推送同一流水号造成并发双写。运行库若已存在 bp_payment，需执行：
    -- ALTER TABLE bp_payment ADD UNIQUE KEY uk_bp_payment_tenant_channel (tenant_id, channel_trade_no);
    UNIQUE KEY uk_bp_payment_tenant_channel (tenant_id, channel_trade_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付单';

-- 集成事件 Outbox（蓝图示范）
CREATE TABLE IF NOT EXISTS bp_outbox (
    id BIGINT PRIMARY KEY COMMENT 'Snowflake ID',
    tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID',
    biz_identity_code VARCHAR(64) COMMENT '业务身份编码',
    event_id VARCHAR(36) NOT NULL COMMENT '事件 UUID',
    event_type VARCHAR(80) NOT NULL COMMENT '事件类型',
    topic VARCHAR(200) NOT NULL COMMENT 'MQ Topic',
    partition_key VARCHAR(100) NOT NULL COMMENT '分区键',
    envelope_json JSON NOT NULL COMMENT '消息信封 JSON',
    schema_version VARCHAR(16) NOT NULL DEFAULT '1.0' COMMENT '事件信封 schema 版本号（Outbox 演进）',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    sent_at DATETIME(3) DEFAULT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_bp_outbox_event_id (event_id),
    KEY idx_bp_outbox_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='蓝图集成事件 Outbox';
