-- 0022_channel_buyer_mapping.sql
--
-- 「渠道买家 ↔ 内部客户」映射表（多渠道交易域补齐）。
--
-- 迁移规程（scripts/migration README）：备份 → Expand → 数据 → 校验 → SIGNAL。
-- 本脚本为纯 Expand（建表 + 索引），可重复执行（CREATE TABLE IF NOT EXISTS 语义）。
--
-- 背景：此前渠道订单的内部客户维度是用买家昵称的稳定哈希
--   （buyerNick.hashCode() & 0x7fffffff + 1000000）算出来的。哈希有三个不可接受的问题：
--   ① 昵称可被买家修改，改名即换客户 → 同一人的订单被拆到不同客户下；
--   ② 哈希值不是真实客户ID，无法与会员/积分/售后/对账 join；
--   ③ 不同租户可能撞值。本表把「渠道买家身份」显式建模为一个独立聚合。
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -uroot -p bone < scripts/migration/0022_channel_buyer_mapping.sql

-- ============ Step 1 · Expand：渠道买家映射表 ============

CREATE TABLE IF NOT EXISTS bp_channel_buyer (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    channel_code        VARCHAR(32)     NOT NULL COMMENT '渠道码：TAOBAO/JD/DOUYIN/PDD',
    channel_buyer_id    VARCHAR(128)    NOT NULL COMMENT '渠道买家账号ID（淘宝 buyer_user_id / 京东 buyerdno / 抖音 buyer_second_id / 拼多多 user_id）',
    channel_buyer_nick  VARCHAR(128)    DEFAULT NULL COMMENT '渠道买家昵称快照（仅供运营识别，昵称可变，不作映射键）',
    customer_id         BIGINT          NOT NULL DEFAULT 0 COMMENT '内部客户ID；0 表示「影子客户（尚未绑定）」',
    customer_name       VARCHAR(128)    DEFAULT NULL COMMENT '内部客户名称快照（绑定时留痕，渠道侧改名不影响）',
    binding_source      VARCHAR(16)     NOT NULL DEFAULT 'MANUAL' COMMENT '绑定来源：MANUAL=人工绑定；AUTO_SHADOW=拉单时自动建的影子映射',
    order_count         INT             NOT NULL DEFAULT 0 COMMENT '累计拉单笔数（用于识别高频渠道买家，优先人工绑定）',
    first_seen_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次观测到该渠道买家的时间',
    last_order_at       DATETIME(3)     DEFAULT NULL COMMENT '最近一次拉单命中该买家的时间',
    remark              VARCHAR(255)    DEFAULT NULL COMMENT '备注（绑定原因、人工登记等）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_buyer (tenant_id, channel_code, channel_buyer_id, deleted),
    KEY idx_channel_buyer_customer (tenant_id, customer_id),
    KEY idx_channel_buyer_unbound (tenant_id, channel_code, customer_id, last_order_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道买家与内部客户映射（多渠道交易域）';

-- ============ Step 2 · Expand：订单表冗余渠道买家ID（对账与排障用） ============

SET @ddl := (
  SELECT IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'channel_buyer_id') > 0,
    'SELECT ''skip add t_order.channel_buyer_id'' AS step',
    'ALTER TABLE t_order ADD COLUMN channel_buyer_id VARCHAR(128) DEFAULT NULL COMMENT ''渠道买家账号ID（对账/排障：同一渠道买家的订单聚合）'' AFTER channel_order_no, ADD KEY idx_order_tenant_channel_buyer (tenant_id, channel_code, channel_buyer_id)'
  ));
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ============ Step 3 · 校验：不变量必须成立，否则 SIGNAL 中止 ============

SET @table_exists := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bp_channel_buyer');
SET @uk_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bp_channel_buyer'
                      AND INDEX_NAME = 'uk_channel_buyer');
SET @order_has_buyer := (SELECT COUNT(*) FROM information_schema.COLUMNS
                          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'channel_buyer_id');

SELECT IF(@table_exists = 1 AND @uk_exists > 0 AND @order_has_buyer = 1,
          'OK: channel buyer mapping schema ready',
          CONCAT('FAILED: table_exists=', @table_exists, ', uk_exists=', @uk_exists,
                 ', order_has_buyer=', @order_has_buyer)) AS migration_check;

SET @abort := IF(@table_exists = 1 AND @uk_exists > 0 AND @order_has_buyer = 1,
                 'DO 0',
                 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0022_channel_buyer_mapping 校验失败：bp_channel_buyer 建表或唯一索引或 t_order 加列未完成''');
PREPARE abort_stmt FROM @abort; EXECUTE abort_stmt; DEALLOCATE PREPARE abort_stmt;
