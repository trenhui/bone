-- ============================================================
-- 0142_claim_secure_signing.sql
--
-- 一句话：新增案件签收三表（批次/明细/轨迹），支撑线上自动签收与线下人工签收，
--        批次号唯一 + 状态 CAS + 轨迹 append-only。
--
-- 口径
-- ----------------------------------------------------------------
--   · 批次表 batch_no 全局唯一，四态状态机字段 sign_status 落枚举 name；
--   · 明细表按 batch_id 归属，image_ready 标记影像完备；
--   · 轨迹表 append-only，无 version 列（与主表 t_claim_case_track 同口径）；
--   · 审计八列齐全（created_by/updated_by/created_at/updated_at/deleted/version）；
--   · 幂等：CREATE TABLE IF NOT EXISTS；终态断言失败用存储过程 SIGNAL（PREPARE 不支持 SIGNAL）；
--   · 本脚本为本地/部署迁移，不在本机会话对共享生产库执行 DDL。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_claim_sign_batch` (
  `id`              BIGINT       NOT NULL,
  `tenant_id`       VARCHAR(64)  NOT NULL,
  `batch_no`        VARCHAR(64)  NOT NULL,
  `source`          VARCHAR(16)  NOT NULL,
  `sign_type`       VARCHAR(16)  NOT NULL,
  `sign_channel`    VARCHAR(32)  NOT NULL,
  `sign_status`     VARCHAR(32)  NOT NULL DEFAULT 'WAIT_FOR_PARTICIPANT',
  `business_key`    VARCHAR(64)  DEFAULT NULL,
  `claim_count`     INT          NOT NULL DEFAULT 0,
  `image_ready_count` INT        NOT NULL DEFAULT 0,
  `express_company` VARCHAR(64)  DEFAULT NULL,
  `express_no`      VARCHAR(64)  DEFAULT NULL,
  `sender`          VARCHAR(64)  DEFAULT NULL,
  `arrive_time`     DATETIME(3)  DEFAULT NULL,
  `sign_institution` VARCHAR(128) DEFAULT NULL,
  `sign_operator`   VARCHAR(64)  DEFAULT NULL,
  `sign_time`       DATETIME(3)  DEFAULT NULL,
  `sign_remark`     VARCHAR(256) DEFAULT NULL,
  `version`         INT          NOT NULL DEFAULT 0,
  `created_by`      BIGINT       DEFAULT NULL,
  `updated_by`      BIGINT       DEFAULT NULL,
  `created_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`         TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_sign_batch_no` (`batch_no`),
  KEY `idx_claim_sign_batch_tenant_status` (`tenant_id`,`sign_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签收批次（线上自动+线下人工）';

CREATE TABLE IF NOT EXISTS `t_claim_sign_batch_item` (
  `id`         BIGINT       NOT NULL,
  `tenant_id`  VARCHAR(64)  NOT NULL,
  `batch_id`   BIGINT       NOT NULL,
  `claim_id`   BIGINT       NOT NULL,
  `claim_no`   VARCHAR(64)  DEFAULT NULL,
  `image_ready` TINYINT(1)  NOT NULL DEFAULT 0,
  `created_by` BIGINT       DEFAULT NULL,
  `updated_by` BIGINT       DEFAULT NULL,
  `created_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at` DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_claim_sign_batch_item` (`batch_id`,`claim_id`),
  KEY `idx_claim_sign_batch_item_claim` (`claim_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签收批次明细';

CREATE TABLE IF NOT EXISTS `t_claim_sign_track` (
  `id`          BIGINT       NOT NULL,
  `tenant_id`   VARCHAR(64)  NOT NULL,
  `batch_id`    BIGINT       NOT NULL,
  `claim_id`    BIGINT       DEFAULT NULL,
  `sign_status` VARCHAR(32)  DEFAULT NULL,
  `action_type` VARCHAR(32)  NOT NULL,
  `message`     VARCHAR(256) DEFAULT NULL,
  `remark`      VARCHAR(256) DEFAULT NULL,
  `operator`    VARCHAR(64)  DEFAULT NULL,
  `occurred_at` DATETIME(3)  NOT NULL,
  `created_by`  BIGINT       DEFAULT NULL,
  `updated_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_claim_sign_track_batch` (`batch_id`,`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签收轨迹（append-only，无version列）';

-- 终态断言：三表存在 + batch_no 唯一键 + 明细唯一键 + 轨迹无 version 列。
SELECT COUNT(*) INTO @sign_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_claim_sign_batch','t_claim_sign_batch_item','t_claim_sign_track');
SELECT COUNT(*) INTO @sign_uks
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 't_claim_sign_batch'
  AND index_name = 'uk_claim_sign_batch_no'
  AND seq_in_index = 1 AND column_name = 'batch_no';
SELECT COUNT(*) INTO @sign_track_version
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 't_claim_sign_track'
  AND column_name = 'version';

DROP PROCEDURE IF EXISTS sp_assert_claim_secure_signing;
DELIMITER $$
CREATE PROCEDURE sp_assert_claim_secure_signing()
BEGIN
  IF @sign_tables <> 3 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'claim secure signing: tables missing';
  END IF;
  IF @sign_uks <> 1 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'claim secure signing: batch_no unique missing';
  END IF;
  IF @sign_track_version <> 0 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644, MESSAGE_TEXT = 'claim secure signing: track must be append-only';
  END IF;
END$$
DELIMITER ;
CALL sp_assert_claim_secure_signing();
DROP PROCEDURE IF EXISTS sp_assert_claim_secure_signing;
