-- ============================================================
-- 0142_procura_business_aggregates.sql
--
-- 一句话：为 procuraAI 新增 Bid / Quotation / Award / CAPA 四张持久事实表，
--        把领域终态与人工终决从进程内内存升级为可重启读回的租户锚定事实。
--
-- 口径
-- ----------------------------------------------------------------
--   · 四表均以 tenant_id 打头建业务唯一键，跨租户同业务键可并存；
--   · 审计八列齐全；AI 评分/推荐只存建议字段，不存模型 output 原文；
--   · 授标与 CAPA 关闭是终态，Adapter 与 DDL 双层拒绝回写；
--   · CREATE TABLE IF NOT EXISTS 幂等；
--   · 本脚本为仓库内 DDL 增量，不执行生产库，不接生产 MQ；
--   · 对应唯一 DDL 真源 bone-init.sql 的同名四表。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_proc_bid` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           BIGINT          NOT NULL,
  `bid_id`              VARCHAR(64)     NOT NULL,
  `rfx_no`              VARCHAR(64)     NOT NULL,
  `supplier_id`         VARCHAR(64)     NOT NULL,
  `source_ref`          VARCHAR(128)    DEFAULT NULL,
  `due_at`              DATETIME(3)     NOT NULL,
  `status`              VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_bid` (`tenant_id`,`bid_id`),
  UNIQUE KEY `uk_proc_bid_supplier` (`tenant_id`,`rfx_no`,`supplier_id`),
  UNIQUE KEY `uk_proc_bid_source` (`tenant_id`,`source_ref`),
  KEY `idx_proc_bid_rfx` (`tenant_id`,`rfx_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='投标事实（到期拒收，来源幂等，撤回终态）';

CREATE TABLE IF NOT EXISTS `t_proc_quotation` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           BIGINT          NOT NULL,
  `quote_id`            VARCHAR(64)     NOT NULL,
  `rfx_no`              VARCHAR(64)     NOT NULL,
  `supplier_id`         VARCHAR(64)     NOT NULL,
  `amount`              DECIMAL(19,4)   NOT NULL,
  `currency`            CHAR(3)         NOT NULL,
  `valid_until`         DATETIME(3)     NOT NULL,
  `ai_score`            DECIMAL(9,6)    DEFAULT NULL,
  `ai_recommendation`   VARCHAR(64)     DEFAULT NULL,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_quotation` (`tenant_id`,`quote_id`),
  KEY `idx_proc_quotation_rfx` (`tenant_id`,`rfx_no`,`currency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报价事实（比价只读，过期与币种不一致不可比）';

CREATE TABLE IF NOT EXISTS `t_proc_award` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           BIGINT          NOT NULL,
  `award_id`            VARCHAR(128)    NOT NULL,
  `rfx_no`              VARCHAR(64)     NOT NULL,
  `supplier_id`         VARCHAR(64)     NOT NULL,
  `confirmed_by`        VARCHAR(64)     NOT NULL,
  `confirmed_at`        DATETIME(3)     NOT NULL,
  `reason`              VARCHAR(512)    NOT NULL,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_award` (`tenant_id`,`award_id`),
  UNIQUE KEY `uk_proc_award_rfx` (`tenant_id`,`rfx_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='授标事实（具名人工终决，终态不可回写）';

CREATE TABLE IF NOT EXISTS `t_proc_capa` (
  `id`                  BIGINT          NOT NULL,
  `tenant_id`           BIGINT          NOT NULL,
  `capa_no`             VARCHAR(64)     NOT NULL,
  `supplier_id`         VARCHAR(64)     NOT NULL,
  `issue_description`   VARCHAR(512)    NOT NULL,
  `owner_id`            VARCHAR(64)     NOT NULL,
  `due_at`              DATETIME(3)     NOT NULL,
  `status`              VARCHAR(32)     NOT NULL DEFAULT 'OPEN',
  `follow_up_required`  TINYINT(1)      NOT NULL DEFAULT 0,
  `closed_by`           VARCHAR(64)     DEFAULT NULL,
  `closed_at`           DATETIME(3)     DEFAULT NULL,
  `evidence_ref`        VARCHAR(256)    DEFAULT NULL,
  `created_by`          BIGINT          DEFAULT NULL,
  `updated_by`          BIGINT          DEFAULT NULL,
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0,
  `version`             INT             NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_capa` (`tenant_id`,`capa_no`),
  KEY `idx_proc_capa_status_due` (`tenant_id`,`status`,`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='CAPA事实（超期转人工，关闭需具名与证据，不自动改变供应商状态）';

-- 终态断言：四张表均存在，且业务唯一键以 tenant_id 打头。
SELECT COUNT(*) INTO @business_tables
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('t_proc_bid','t_proc_quotation','t_proc_award','t_proc_capa');
SELECT COUNT(*) INTO @business_uks
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name IN ('t_proc_bid','t_proc_quotation','t_proc_award','t_proc_capa')
  AND index_name IN ('uk_proc_bid','uk_proc_quotation','uk_proc_award','uk_proc_capa')
  AND seq_in_index = 1 AND column_name = 'tenant_id';
