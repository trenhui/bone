-- ============================================================
-- 0141_procura_contract_review_event_chain.sql
--
-- 一句话：为 ContractReviewed 增加持久化事件闭环三表（Outbox / Inbox / DLQ），
--        把进程内 ConcurrentHashMap 直连升级为「事务发布 → 幂等消费 → 失败隔离可重放」。
--
-- 背景（为什么必须做）
-- ----------------------------------------------------------------
-- 既有 ContractReviewedConsumer 用进程内 ConcurrentHashMap 存结论，override 直连
-- consume()，重启即丢、发布失败即永久丢事件、消费失败无重试与隔离，无法证明
-- 「重复投递只消费一次 / 跨租户拒绝留痕 / 失败入 DLQ 可按 review_id 重放」。
--
-- 口径
-- ----------------------------------------------------------------
--   · 三表均以 (tenant_id, review_id) 为业务唯一键，审计八列齐全；
--   · Outbox.payload_json 只存既有 7 字段快照（禁 output 原文 / PII / 合同正文）；
--   · Inbox 条件插入裁决幂等，结论重启可恢复；DLQ 存失败理由与重放状态；
--   · 幂等：CREATE TABLE IF NOT EXISTS，可重复执行；
--   · 本脚本为本地 init 基线，不接生产 MQ 凭证、不执行生产库操作，
--     投递由显式驱动扫描模拟，真实 MQ listener 仍属 L3。
--   · 对应唯一 DDL 真源 bone-init.sql 的 t_proc_contract_outbox / inbox / dlq 三表。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_proc_contract_outbox` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           VARCHAR(64)     NOT NULL,
  `review_id`           VARCHAR(64)     NOT NULL,
  `payload_json`        TEXT            NOT NULL,
  `delivery_status`     VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
  `delivered_at`        DATETIME(3)     DEFAULT NULL,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_contract_outbox` (`tenant_id`,`review_id`),
  KEY `idx_proc_contract_outbox_status` (`tenant_id`,`delivery_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ContractReviewed Outbox（事务性发布；本地显式驱动扫描）';

CREATE TABLE IF NOT EXISTS `t_proc_contract_inbox` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           VARCHAR(64)     NOT NULL,
  `review_id`           VARCHAR(64)     NOT NULL,
  `risk_level`          VARCHAR(32)     NOT NULL,
  `ai_participation`    VARCHAR(32)     NOT NULL,
  `review_snapshot_ref` VARCHAR(256)    DEFAULT NULL,
  `quarantined`         TINYINT(1)      NOT NULL DEFAULT 0,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_contract_inbox` (`tenant_id`,`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ContractReviewed Inbox（结论去重态；重启可恢复）';

CREATE TABLE IF NOT EXISTS `t_proc_contract_dlq` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           VARCHAR(64)     NOT NULL,
  `review_id`           VARCHAR(64)     NOT NULL,
  `failure_reason`      VARCHAR(512)    NOT NULL,
  `outbox_ref`          VARCHAR(256)    NOT NULL,
  `resolved`            TINYINT(1)      NOT NULL DEFAULT 0,
  `occurred_at`         DATETIME(3)     NOT NULL,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_contract_dlq` (`tenant_id`,`review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='ContractReviewed DLQ（失败隔离与按 review_id 重放）';

-- 终态断言：三张表均存在，且 (tenant_id, review_id) 唯一键齐全。
SELECT COUNT(*) INTO @event_chain_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_proc_contract_outbox','t_proc_contract_inbox','t_proc_contract_dlq');
SELECT COUNT(*) INTO @event_chain_uks
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('t_proc_contract_outbox','t_proc_contract_inbox','t_proc_contract_dlq')
  AND index_name IN ('uk_proc_contract_outbox','uk_proc_contract_inbox','uk_proc_contract_dlq')
  AND seq_in_index = 1 AND column_name = 'tenant_id';
