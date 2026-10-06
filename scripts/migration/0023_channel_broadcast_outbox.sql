-- 0023_channel_broadcast_outbox.sql
--
-- 渠道库存广播任务表（Outbox 化：库存变更 → 同事务入队 → 中继异步投递 → 重试/死信/人工重试）。
--
-- 迁移规程（scripts/migration README）：备份 → Expand → 数据 → 校验 → SIGNAL。
-- 纯 Expand（建表 + 索引），可重复执行。
--
-- 背景：原先库存变更后由 ChannelProductApplicationService#syncInventoryToAllChannels 在**业务事务内**
-- 同步循环调用每个渠道的 syncInventory。三个问题：
--   ① 渠道 HTTP 超时/限流直接拖长库存事务（连接池被同步 IO 占用），最坏情况库存事务回滚而渠道已改成功 → 两侧不一致；
--   ② 4 个渠道串行，最慢的一个决定整体延迟；
--   ③ 失败只留日志，无重试、无死信、无人工干预入口 —— 渠道库存长期停留在旧值，直到超卖才被发现。
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -uroot -p bone < scripts/migration/0023_channel_broadcast_outbox.sql

-- ============ Step 1 · Expand：广播任务表 ============

CREATE TABLE IF NOT EXISTS bp_channel_broadcast_task (
    id                  BIGINT          NOT NULL COMMENT '主键（Snowflake）',
    tenant_id           BIGINT          NOT NULL DEFAULT 0 COMMENT '租户ID',
    product_id          BIGINT          NOT NULL COMMENT '内部商品ID',
    channel_code        VARCHAR(32)     NOT NULL COMMENT '渠道码：TAOBAO/JD/DOUYIN/PDD',
    channel_product_id  VARCHAR(128)    DEFAULT NULL COMMENT '渠道侧商品ID（投递时用；为空表示尚未上架成功，不应入队）',
    product_name        VARCHAR(255)    DEFAULT NULL COMMENT '商品名称快照（投递失败时便于运营定位）',
    target_stock        INT             NOT NULL DEFAULT 0 COMMENT '目标库存（覆盖式：渠道库存应为这个值，不是增量）',
    status              VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '投递状态：PENDING/PROCESSING/SENT/FAILED',
    retry_count         INT             NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry           INT             NOT NULL DEFAULT 5 COMMENT '最大重试次数，超过置 FAILED（死信）',
    next_retry_at       DATETIME(3)     DEFAULT NULL COMMENT '下次可重试时间（指数退避）',
    last_error          VARCHAR(512)    DEFAULT NULL COMMENT '最近一次失败原因（渠道原始 message）',
    merged_into_id      BIGINT          DEFAULT NULL COMMENT '被合并到哪条任务（同商品同渠道只投最新库存，旧的标 SENT 并记此字段）',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（CORE-07）',
    created_by          BIGINT          DEFAULT NULL COMMENT '创建人ID',
    updated_by          BIGINT          DEFAULT NULL COMMENT '修改人ID',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间（合并时刷新，中继据此取最新）',
    deleted             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_broadcast_pending (status, next_retry_at),
    KEY idx_broadcast_product (tenant_id, product_id, channel_code, status),
    KEY idx_broadcast_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='渠道库存广播任务（Outbox 异步投递）';

-- ============ Step 2 · 校验：不变量必须成立，否则 SIGNAL 中止 ============

SET @table_exists := (SELECT COUNT(*) FROM information_schema.TABLES
                      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bp_channel_broadcast_task');
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bp_channel_broadcast_task'
                      AND INDEX_NAME = 'idx_broadcast_pending');
SET @merged_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bp_channel_broadcast_task'
                      AND COLUMN_NAME = 'merged_into_id');

SELECT IF(@table_exists = 1 AND @idx_exists > 0 AND @merged_col = 1,
          'OK: channel broadcast outbox schema ready',
          CONCAT('FAILED: table_exists=', @table_exists, ', idx_exists=', @idx_exists,
                 ', merged_col=', @merged_col)) AS migration_check;

SET @abort := IF(@table_exists = 1 AND @idx_exists > 0 AND @merged_col = 1,
                 'DO 0',
                 'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0023_channel_broadcast_outbox 校验失败：bp_channel_broadcast_task 建表或索引或列未完成''');
PREPARE abort_stmt FROM @abort; EXECUTE abort_stmt; DEALLOCATE PREPARE abort_stmt;
