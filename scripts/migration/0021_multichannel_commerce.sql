-- 0021_multichannel_commerce.sql
--
-- 多渠道交易域：渠道注册 / 渠道商品上架 / 库存 / 发货物流。
--
-- 迁移规程（scripts/migration README）：备份 → Expand → 数据 → 校验 → SIGNAL。
-- 本脚本全部为 Expand（加列 / 建表），无破坏性操作，可重复执行（IF NOT EXISTS 语义由
-- information_schema 判空 + PREPARE 实现，因为 MySQL 不支持 ADD COLUMN IF NOT EXISTS）。
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -uroot -p bone < scripts/migration/0021_multichannel_commerce.sql

-- ============ Step 1 · Expand：t_order 增加多渠道字段 ============

SET @ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'channel_code') > 0,
    'SELECT ''skip add t_order.channel_code'' AS step',
    'ALTER TABLE t_order ADD COLUMN channel_code VARCHAR(32) DEFAULT NULL COMMENT ''销售渠道码：TAOBAO/JD/DOUYIN/PDD（与 channel_source 不同：后者是下单终端）'' AFTER channel_source, ADD KEY idx_order_tenant_channel_code (tenant_id, channel_code)'
  ));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'channel_order_no') > 0,
    'SELECT ''skip add t_order.channel_order_no'' AS step',
    'ALTER TABLE t_order ADD COLUMN channel_order_no VARCHAR(64) DEFAULT NULL COMMENT ''渠道原始订单号（渠道侧主键，回传/对账用）'' AFTER channel_code, ADD UNIQUE KEY uk_order_tenant_channel_order_no (tenant_id, channel_code, channel_order_no)'
  ));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ Step 2 · Expand：渠道注册表 ============

CREATE TABLE IF NOT EXISTS bp_channel (
    id                  BIGINT          NOT NULL COMMENT '渠道主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    channel_code        VARCHAR(32)     NOT NULL COMMENT '渠道码：TAOBAO/JD/DOUYIN/PDD',
    channel_name        VARCHAR(64)     NOT NULL COMMENT '渠道中文名',
    ext_impl_code       VARCHAR(64)     DEFAULT NULL COMMENT '命中的扩展实现 code（扩展点路由结果，可观测用）',
    api_endpoint        VARCHAR(255)    DEFAULT NULL COMMENT '渠道开放平台网关地址',
    app_key             VARCHAR(128)    DEFAULT NULL COMMENT '渠道 appKey（生产应走密钥管理，此处仅联调占位）',
    enabled             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用（停用后不参与拉单/上架/发货）',
    order_sync_enabled  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否开启订单自动同步',
    last_sync_at        DATETIME(3)     DEFAULT NULL COMMENT '最近一次拉单时间',
    remark              VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_tenant_code (tenant_id, channel_code, deleted),
    KEY idx_channel_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='销售渠道注册（多渠道交易域）';

-- ============ Step 3 · Expand：渠道商品上架 ============

CREATE TABLE IF NOT EXISTS bp_channel_product (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    channel_code        VARCHAR(32)     NOT NULL COMMENT '渠道码',
    product_id          BIGINT          NOT NULL COMMENT '内部商品ID',
    product_name        VARCHAR(200)    NOT NULL COMMENT '商品名称（上架时快照）',
    channel_product_id  VARCHAR(64)     DEFAULT NULL COMMENT '渠道侧商品ID（上架成功后回填；下架后清空）',
    listing_status      VARCHAR(20)     NOT NULL DEFAULT 'UNLISTED' COMMENT 'UNLISTED/LISTING/ONLINE/DELISTING/OFFLINE/FAILED',
    listing_price       DECIMAL(18,2)   DEFAULT NULL COMMENT '渠道挂牌价',
    listing_stock       INT             NOT NULL DEFAULT 0 COMMENT '最近一次同步到渠道的库存（对账用）',
    last_sync_at        DATETIME(3)     DEFAULT NULL COMMENT '最近一次同步时间',
    fail_reason         VARCHAR(500)    DEFAULT NULL COMMENT '最近一次失败原因（FAILED 态必填）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cproduct_tenant_channel_product (tenant_id, channel_code, product_id, deleted),
    KEY idx_cproduct_tenant_status (tenant_id, listing_status),
    KEY idx_cproduct_tenant_channel_status (tenant_id, channel_code, listing_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道商品上架（多渠道交易域）';

-- ============ Step 4 · Expand：库存 ============

CREATE TABLE IF NOT EXISTS bp_inventory (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    product_id          BIGINT          NOT NULL COMMENT '商品ID',
    product_name        VARCHAR(200)    DEFAULT NULL COMMENT '商品名称（冗余展示，主数据在 masterdata）',
    warehouse_code      VARCHAR(32)     NOT NULL DEFAULT 'DEFAULT' COMMENT '仓库编码',
    available_qty       INT             NOT NULL DEFAULT 0 COMMENT '可用库存 = 总库存 - 预留',
    reserved_qty        INT             NOT NULL DEFAULT 0 COMMENT '已预留（已下单未出库）',
    safety_stock        INT             NOT NULL DEFAULT 0 COMMENT '安全库存（低于此值触发补货预警）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_tenant_product_warehouse (tenant_id, product_id, warehouse_code, deleted),
    KEY idx_inventory_tenant_available (tenant_id, available_qty)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存（多渠道交易域，替代 MockInventoryGatewayAdapter）';

-- ============ Step 5 · Expand：发货单 ============

CREATE TABLE IF NOT EXISTS bp_shipment (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    order_id            BIGINT          NOT NULL COMMENT '关联 t_order.id',
    channel_code        VARCHAR(32)     DEFAULT NULL COMMENT '渠道码（渠道订单发货回传用）',
    shipment_no         VARCHAR(32)     DEFAULT NULL COMMENT '发货单号（SF + yyyyMMdd + 雪花ID）',
    logistics_company   VARCHAR(64)     DEFAULT NULL COMMENT '物流公司',
    tracking_no         VARCHAR(64)     DEFAULT NULL COMMENT '运单号',
    status              VARCHAR(20)     NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/SHIPPED/IN_TRANSIT/SIGNED/FAILED',
    receiver_name       VARCHAR(64)     DEFAULT NULL COMMENT '收货人',
    receiver_phone      VARCHAR(32)     DEFAULT NULL COMMENT '收货电话',
    receiver_address    VARCHAR(500)    DEFAULT NULL COMMENT '收货地址',
    channel_ack         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已回传渠道（1=已回传）',
    channel_ack_at      DATETIME(3)     DEFAULT NULL COMMENT '渠道回传时间',
    shipped_at          DATETIME(3)     DEFAULT NULL COMMENT '发货时间',
    signed_at           DATETIME(3)     DEFAULT NULL COMMENT '签收时间',
    fail_reason         VARCHAR(500)    DEFAULT NULL COMMENT '失败原因',
    remark              VARCHAR(500)    DEFAULT NULL COMMENT '备注',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_tenant_order (tenant_id, order_id, deleted),
    UNIQUE KEY uk_shipment_tenant_no (tenant_id, shipment_no),
    KEY idx_shipment_tenant_status (tenant_id, status),
    KEY idx_shipment_tenant_channel (tenant_id, channel_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='发货单（多渠道交易域）';

-- ============ Step 6 · Expand：物流轨迹 ============

CREATE TABLE IF NOT EXISTS bp_shipment_trace (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    shipment_id         BIGINT          NOT NULL COMMENT '关联 bp_shipment.id',
    trace_time          DATETIME(3)     NOT NULL COMMENT '轨迹发生时间',
    trace_status        VARCHAR(32)     DEFAULT NULL COMMENT '轨迹状态（渠道原始状态码）',
    trace_desc          VARCHAR(500)    NOT NULL COMMENT '轨迹描述',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_trace_tenant_shipment (tenant_id, shipment_id),
    KEY idx_trace_time (trace_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='物流轨迹（多渠道交易域）';

-- ============ Step 7 · 数据：预置四大渠道 ============
-- 幂等：以 (tenant_id, channel_code) 唯一键做存在性判定，重跑不会重复插入。

INSERT INTO bp_channel (id, tenant_id, channel_code, channel_name, api_endpoint, app_key,
                        enabled, order_sync_enabled, version, created_at, updated_at, deleted)
SELECT 1000000000000000001, 0, 'TAOBAO', '淘宝', 'https://eco.taobao.com/router/rest', 'demo-taobao-app-key', 1, 0, 0, NOW(3), NOW(3), 0
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) AS dummy
                  WHERE EXISTS (SELECT 1 FROM bp_channel c WHERE c.tenant_id = 0 AND c.channel_code = 'TAOBAO' AND c.deleted = 0));

INSERT INTO bp_channel (id, tenant_id, channel_code, channel_name, api_endpoint, app_key,
                        enabled, order_sync_enabled, version, created_at, updated_at, deleted)
SELECT 1000000000000000002, 0, 'JD', '京东', 'https://router.jd.com/api', 'demo-jd-app-key', 1, 0, 0, NOW(3), NOW(3), 0
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) AS dummy
                  WHERE EXISTS (SELECT 1 FROM bp_channel c WHERE c.tenant_id = 0 AND c.channel_code = 'JD' AND c.deleted = 0));

INSERT INTO bp_channel (id, tenant_id, channel_code, channel_name, api_endpoint, app_key,
                        enabled, order_sync_enabled, version, created_at, updated_at, deleted)
SELECT 1000000000000000003, 0, 'DOUYIN', '抖音', 'https://openapi-fxg.jinritemai.com', 'demo-douyin-app-key', 1, 0, 0, NOW(3), NOW(3), 0
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) AS dummy
                  WHERE EXISTS (SELECT 1 FROM bp_channel c WHERE c.tenant_id = 0 AND c.channel_code = 'DOUYIN' AND c.deleted = 0));

INSERT INTO bp_channel (id, tenant_id, channel_code, channel_name, api_endpoint, app_key,
                        enabled, order_sync_enabled, version, created_at, updated_at, deleted)
SELECT 1000000000000000004, 0, 'PDD', '拼多多', 'https://gw-api.pinduoduo.com/api/router', 'demo-pdd-app-key', 1, 0, 0, NOW(3), NOW(3), 0
WHERE NOT EXISTS (SELECT 1 FROM (SELECT 1) AS dummy
                  WHERE EXISTS (SELECT 1 FROM bp_channel c WHERE c.tenant_id = 0 AND c.channel_code = 'PDD' AND c.deleted = 0));

-- ============ Step 8 · 校验：不变量必须成立，否则 SIGNAL 中止 ============

SET @channel_count := (SELECT COUNT(*) FROM bp_channel WHERE tenant_id = 0 AND deleted = 0 AND channel_code IN ('TAOBAO','JD','DOUYIN','PDD'));
SET @order_has_channel_code := (SELECT COUNT(*) FROM information_schema.COLUMNS
                                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'channel_code');

SET @check_msg := IF(@channel_count = 4 AND @order_has_channel_code = 1,
                     'OK: multichannel commerce schema ready',
                     CONCAT('FAILED: channel_count=', @channel_count, ', order_has_channel_code=', @order_has_channel_code));

SELECT @check_msg AS migration_check;

SET @abort := IF(@channel_count = 4 AND @order_has_channel_code = 1,
                 'DO 0',
                 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0021_multichannel_commerce 校验失败：渠道预置或 t_order 加列未完成''');
PREPARE abort_stmt FROM @abort; EXECUTE abort_stmt; DEALLOCATE PREPARE abort_stmt;
