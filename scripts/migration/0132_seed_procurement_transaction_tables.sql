-- ============================================================
-- 0132_seed_procurement_transaction_tables.sql
--
-- 目的：把 bone-init.sql 中已定义的 15 张采购平台表补进 baseline 库。
--   baseline 环境在采购模块合流前记账，库里没有 t_proc_*，导致交易/履约/寻源等接口 404。
-- 口径：DDL 逐字取自 bone-init.sql，仅加 IF NOT EXISTS；重复执行安全。
-- 边界：只补表结构，不写业务数据；业务数据由 UI/E2E 操作生成。
-- ============================================================

USE bone;

CREATE TABLE IF NOT EXISTS `t_proc_requisition` (
  `id`                  BIGINT          NOT NULL COMMENT '请购单主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `pr_no`               VARCHAR(64)     NOT NULL COMMENT '请购单号 PR-{yyyy}-{6位}',
  `org_id`              BIGINT          NOT NULL COMMENT '组织ID',
  `requester_id`        BIGINT          NOT NULL COMMENT '请购人ID',
  `status`              VARCHAR(32)     NOT NULL COMMENT '状态：DRAFT/SUBMITTED/APPROVED/CONVERTED/CANCELLED',
  `total_amount`        DECIMAL(19,4)   NOT NULL COMMENT '请购总额（分位与行合计一致）',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `budget_ref`          VARCHAR(128)    DEFAULT NULL COMMENT '预算引用（占位，L19 待决，不做真实占用）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_requisition_no` (`tenant_id`,`pr_no`),
  KEY `idx_proc_requisition_org_status` (`tenant_id`,`org_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='请购单主表（采购交易 PR+PO 切片）';

CREATE TABLE IF NOT EXISTS `t_proc_po` (
  `id`                  BIGINT          NOT NULL COMMENT '采购订单主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `po_no`               VARCHAR(64)     NOT NULL COMMENT '订单号 PO-{yyyy}-{6位}',
  `pr_no`               VARCHAR(64)     DEFAULT NULL COMMENT '来源请购单号（直接建单为 NULL）',
  `org_id`              BIGINT          NOT NULL COMMENT '采购组织ID',
  `vendor_party_id`     VARCHAR(64)     NOT NULL COMMENT '供应商 partyId（经 VendorQueryPort 校验）',
  `legal_entity_id`     BIGINT          NOT NULL COMMENT '采购方法人实体ID（跨法人隔离比对）',
  `status`              VARCHAR(32)     NOT NULL COMMENT '状态：DRAFT/SUBMITTED/APPROVED/SENT/CANCELLED/ACKNOWLEDGED/PARTIAL_RECEIVED/FULLY_RECEIVED/CLOSED',
  `po_amount`           DECIMAL(19,4)   NOT NULL COMMENT '订单总额 = Σ line_amount（强一致不变量）',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `tax_amount`          DECIMAL(19,4)   NOT NULL DEFAULT 0 COMMENT '税额合计',
  `fx_rate`             DECIMAL(19,8)   DEFAULT NULL COMMENT '汇率（下达时冻结；本币为 NULL）',
  `fx_as_of`            DATETIME(3)     DEFAULT NULL COMMENT '汇率取数时点 UTC',
  `fx_source`           VARCHAR(64)     DEFAULT NULL COMMENT '汇率来源（本期占位）',
  `tax_code_version`    VARCHAR(64)     DEFAULT NULL COMMENT '税码版本（快照哈希输入）',
  `budget_ref`          VARCHAR(128)    DEFAULT NULL COMMENT '预算引用（占位，L19 待决）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本（快照 po_version 取此值）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_po_no` (`tenant_id`,`po_no`),
  KEY `idx_proc_po_vendor_status` (`tenant_id`,`vendor_party_id`,`status`),
  KEY `idx_proc_po_org_status` (`tenant_id`,`org_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单主表（主表仅按单号唯一，不带 po_version）';

CREATE TABLE IF NOT EXISTS `t_proc_po_line` (
  `id`                  BIGINT          NOT NULL COMMENT '订单行主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `po_id`               BIGINT          NOT NULL COMMENT '订单主表ID',
  `line_no`             INT             NOT NULL COMMENT '行号（单内唯一，从 1 起）',
  `item_id`             VARCHAR(64)     NOT NULL COMMENT '物料ID',
  `item_name`           VARCHAR(256)    DEFAULT NULL COMMENT '物料名称（冗余展示，非 PII）',
  `quantity`            DECIMAL(19,6)   NOT NULL COMMENT '数量',
  `unit`                VARCHAR(16)     DEFAULT NULL COMMENT '计量单位',
  `net_price`           DECIMAL(19,4)   NOT NULL COMMENT '净价（本期直接取订单行输入，不经条件引擎）',
  `tax_rate`            DECIMAL(19,6)   NOT NULL DEFAULT 0 COMMENT '税率',
  `line_amount`         DECIMAL(19,4)   NOT NULL COMMENT '行金额 = round(net_price × quantity × (1 + tax_rate), 4)，逐行舍入',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_po_line` (`tenant_id`,`po_id`,`line_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购订单行（逐行快照，先舍入再汇总）';

CREATE TABLE IF NOT EXISTS `t_proc_price_snapshot` (
  `id`                  BIGINT          NOT NULL COMMENT '快照主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `po_no`               VARCHAR(64)     NOT NULL COMMENT '订单号',
  `po_version`          INT             NOT NULL COMMENT '下达时 t_proc_po.version 的值（非独立业务修订列）',
  `lines_json`          TEXT            NOT NULL COMMENT '逐行净价结果 JSON（itemId/qty/netPrice/lineAmount）',
  `tax_amount`          DECIMAL(19,4)   NOT NULL COMMENT '税额合计',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `fx_rate`             DECIMAL(19,8)   DEFAULT NULL COMMENT '汇率',
  `fx_as_of`            DATETIME(3)     DEFAULT NULL COMMENT '汇率取数时点 UTC',
  `fx_source`           VARCHAR(64)     DEFAULT NULL COMMENT '汇率来源',
  `rounding_mode`       VARCHAR(32)     NOT NULL DEFAULT 'HALF_UP' COMMENT '舍入口径',
  `tax_code_version`    VARCHAR(64)     DEFAULT NULL COMMENT '税码版本',
  `computed_hash`       VARCHAR(64)     NOT NULL COMMENT 'SHA-256 规范化哈希（可复现断言）',
  `computed_at`         DATETIME(3)     NOT NULL COMMENT '计算时点 UTC',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_price_snapshot` (`tenant_id`,`po_no`,`po_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='价格快照（只追加不可变，无 update/delete 入口）';

CREATE TABLE IF NOT EXISTS `t_proc_po_ack` (
  `id`                  BIGINT          NOT NULL COMMENT 'PO确认主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `po_no`               VARCHAR(64)     NOT NULL COMMENT '订单号',
  `po_version`          INT             NOT NULL COMMENT '确认时 PO 版本（幂等键）',
  `vendor_party_id`     VARCHAR(64)     NOT NULL COMMENT '确认供应商 partyId（身份绑定）',
  `ack_type`            VARCHAR(16)     NOT NULL COMMENT 'FULL/PARTIAL/REJECT',
  `ack_lines_json`      TEXT            DEFAULT NULL COMMENT '行级确认结果 JSON',
  `ack_at`              DATETIME(3)     NOT NULL COMMENT '确认时点 UTC',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_po_ack` (`tenant_id`,`po_no`,`po_version`,`vendor_party_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PO行级确认（855类比；po_no+po_version+vendor 幂等）';

CREATE TABLE IF NOT EXISTS `t_proc_asn` (
  `id`                  BIGINT          NOT NULL COMMENT 'ASN主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `asn_no`              VARCHAR(64)     NOT NULL COMMENT '发货通知号（业务唯一键）',
  `po_no`               VARCHAR(64)     NOT NULL COMMENT '订单号（引用已确认PO）',
  `vendor_party_id`     VARCHAR(64)     NOT NULL COMMENT '供应商 partyId',
  `ship_date`           DATE            DEFAULT NULL COMMENT '发货日期',
  `carrier`             VARCHAR(64)     DEFAULT NULL COMMENT '承运商',
  `tracking_no`         VARCHAR(128)    DEFAULT NULL COMMENT '物流追踪号',
  `sscc_json`           TEXT            DEFAULT NULL COMMENT 'SSCC-18 包装标识 JSON',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'CREATED/TRANSMITTED/RECEIVING_MATCHED/RECONCILED/CANCELLED',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_asn` (`tenant_id`,`asn_no`),
  KEY `idx_proc_asn_po` (`tenant_id`,`po_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货通知（856类比；asn_no 唯一，按 po_no 保序）';

CREATE TABLE IF NOT EXISTS `t_proc_asn_line` (
  `id`                  BIGINT          NOT NULL COMMENT 'ASN行主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `asn_id`              BIGINT          NOT NULL COMMENT 'ASN主键',
  `line_no`             INT             NOT NULL COMMENT '行号（单内唯一，从 1 起）',
  `po_line_no`          INT             NOT NULL COMMENT '引用 PO 行号',
  `item_id`             VARCHAR(64)     NOT NULL COMMENT '物料ID',
  `qty`                 DECIMAL(19,6)   NOT NULL COMMENT '发货数量',
  `uom`                 VARCHAR(16)     DEFAULT NULL COMMENT '计量单位',
  `pack_ref`            VARCHAR(64)     DEFAULT NULL COMMENT '包装层级引用（SSCC）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_asn_line` (`tenant_id`,`asn_id`,`line_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发货通知行（随 ASN 根，数量 DECIMAL(19,6)）';

CREATE TABLE IF NOT EXISTS `t_proc_invoice_submission` (
  `id`                  BIGINT          NOT NULL COMMENT '发票提交主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `invoice_no`          VARCHAR(64)     NOT NULL COMMENT '发票号',
  `vendor_party_id`     VARCHAR(64)     NOT NULL COMMENT '供应商 partyId（去重键组成）',
  `po_no`               VARCHAR(64)     DEFAULT NULL COMMENT '引用订单号',
  `amount`              DECIMAL(19,4)   NOT NULL COMMENT '发票金额（TDS）',
  `tax`                 DECIMAL(19,4)   NOT NULL DEFAULT 0 COMMENT '税额',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `lines_json`          TEXT            DEFAULT NULL COMMENT '行项目 JSON（逐行金额，TDS=ΣIT1±SAC）',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'DRAFT/SUBMITTED/MATCH_PENDING/MATCHED/BLOCKED/REJECTED',
  `submitted_at`        DATETIME(3)     DEFAULT NULL COMMENT '提交时点 UTC',
  `finance_event_id`    VARCHAR(64)     DEFAULT NULL COMMENT 'Finance 匹配回写事件ID（只回写标识，非空即已回写）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_invoice_dedup` (`tenant_id`,`invoice_no`,`vendor_party_id`),
  KEY `idx_proc_invoice_po_status` (`tenant_id`,`po_no`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商自助开票（810类比；只提交不裁决，金额 DECIMAL(19,4)）';

CREATE TABLE IF NOT EXISTS `t_proc_ir_exception` (
  `id`                  BIGINT          NOT NULL COMMENT '发票例外主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `exception_no`        VARCHAR(64)     NOT NULL COMMENT '例外单号（业务唯一键）',
  `invoice_no`          VARCHAR(64)     NOT NULL COMMENT '关联发票号',
  `exception_code`      VARCHAR(64)     NOT NULL COMMENT '例外码（同发票内幂等键）',
  `match_status`        VARCHAR(32)     DEFAULT NULL COMMENT 'Finance 回传匹配状态 MATCHED/EXCEPTION/REVIEW',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'OPEN/SUPPLEMENTED/UNDER_REVIEW/RESOLVED/REJECTED',
  `supplement_ref`      VARCHAR(256)    DEFAULT NULL COMMENT '补料引用（不放行）',
  `finance_event_id`    VARCHAR(64)     DEFAULT NULL COMMENT 'Finance 回写事件ID（RESOLVED 唯一来源）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_ir_exception_no` (`tenant_id`,`exception_no`),
  UNIQUE KEY `uk_proc_ir_exception_dedup` (`tenant_id`,`invoice_no`,`exception_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='发票例外台账（状态只由 Finance 事件回写，采购域无放行入口）';

CREATE TABLE IF NOT EXISTS `t_proc_sourcing_project` (
  `id`                  BIGINT          NOT NULL COMMENT '寻源项目主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `project_no`          VARCHAR(64)     NOT NULL COMMENT '项目编号（业务唯一键）',
  `category_id`         VARCHAR(64)     NOT NULL COMMENT '品类ID',
  `budget_amount`       DECIMAL(19,4)   NOT NULL COMMENT '预算金额（TDS）',
  `currency`            CHAR(3)         NOT NULL COMMENT '币种 ISO-4217',
  `strategy`            VARCHAR(32)     NOT NULL COMMENT '寻源策略 SINGLE/COMPETITIVE/AUCTION',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'DRAFT/PUBLISHED/BIDDING_CLOSED/AWARDED/CLOSED/CANCELLED',
  `owner_id`            VARCHAR(64)     DEFAULT NULL COMMENT '项目负责人',
  `window_start`        DATETIME(3)     DEFAULT NULL COMMENT '寻源窗口起 UTC',
  `window_end`          DATETIME(3)     DEFAULT NULL COMMENT '寻源窗口止 UTC（截止后禁止收标）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本（PUBLISHED 后关键字段冻结 CAS）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_sourcing_project_no` (`tenant_id`,`project_no`),
  KEY `idx_proc_sourcing_project_cat_status` (`tenant_id`,`category_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='寻源项目头（关键字段 PUBLISHED 后冻结，@Version CAS）';

CREATE TABLE IF NOT EXISTS `t_proc_rfx` (
  `id`                  BIGINT          NOT NULL COMMENT 'RFx 主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `project_id`          BIGINT          NOT NULL COMMENT '所属寻源项目ID',
  `rfx_no`              VARCHAR(64)     NOT NULL COMMENT 'RFx 编号（业务唯一键）',
  `rfx_type`            VARCHAR(16)     NOT NULL COMMENT 'RFI/RFP/RFQ',
  `publish_at`          DATETIME(3)     DEFAULT NULL COMMENT '发布时间 UTC',
  `due_at`              DATETIME(3)     NOT NULL COMMENT '截止时间 UTC（到期后写报 31201）',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'DRAFT/PUBLISHED/CLOSED/CANCELLED',
  `sealed`              TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '密封标志：开标前报价不可读（含管理员）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_rfx_no` (`tenant_id`,`rfx_no`),
  KEY `idx_proc_rfx_project_due` (`tenant_id`,`project_id`,`due_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='RFx 头（截止时间单调；密封报价不在本表，投标切片后续落库）';

CREATE TABLE IF NOT EXISTS `t_proc_qualification` (
  `id`                  BIGINT          NOT NULL COMMENT '证照主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `supplier_id`         VARCHAR(64)     NOT NULL COMMENT '供应商 partyId',
  `cert_type`           VARCHAR(64)     NOT NULL COMMENT '证照类型',
  `cert_no`             VARCHAR(256)    NOT NULL COMMENT '证照号（加密/脱敏存储，PII 禁入 AI 入参）',
  `issued_at`           DATETIME(3)     DEFAULT NULL COMMENT '发证日 UTC',
  `expires_at`          DATETIME(3)     DEFAULT NULL COMMENT '到期日 UTC（到期巡检；EXPIRED 不得作准入依据）',
  `verify_status`       VARCHAR(32)     NOT NULL COMMENT 'PENDING/VERIFIED/REJECTED/EXPIRED',
  `attachment_ref`      VARCHAR(256)    DEFAULT NULL COMMENT '附件引用（不放行原文）',
  `ocr_result_json`     TEXT            DEFAULT NULL COMMENT 'OCR 结构化结果（须人工复核才生效）',
  `reviewed_by`         VARCHAR(64)     DEFAULT NULL COMMENT '人工复核人（具名，OCR 不得自证）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_qualification_dedup` (`tenant_id`,`supplier_id`,`cert_type`,`cert_no`),
  KEY `idx_proc_qualification_expires` (`tenant_id`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商证照（到期巡检；OCR 结论须人工复核才生效）';

CREATE TABLE IF NOT EXISTS `t_proc_onboarding` (
  `id`                  BIGINT          NOT NULL COMMENT '准入单主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `supplier_id`         VARCHAR(64)     NOT NULL COMMENT '供应商 partyId',
  `onboarding_no`       VARCHAR(64)     NOT NULL COMMENT '准入单号（业务唯一键）',
  `background_ref`      VARCHAR(256)    DEFAULT NULL COMMENT '背调报告引用（只存引用与哈希，不回传 PII 原文）',
  `score`               DECIMAL(9,4)    DEFAULT NULL COMMENT '准入评分（AI 仅建议，终决必须人工）',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'DRAFT/IN_REVIEW/APPROVED/REJECTED',
  `workflow_instance_id` VARCHAR(64)    DEFAULT NULL COMMENT 'WF 流程实例ID（状态由 WF 回写）',
  `approved_by`         VARCHAR(64)     DEFAULT NULL COMMENT '人工终决人（具名；AI 不得置 APPROVED）',
  `in_review_key`       VARCHAR(64)     GENERATED ALWAYS AS (IF(`status` = 'IN_REVIEW', `supplier_id`, NULL)) STORED COMMENT 'IN_REVIEW 条件唯一键（生成列实现部分唯一，防并发重复准入）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_onboarding_no` (`tenant_id`,`onboarding_no`),
  UNIQUE KEY `uk_proc_onboarding_in_review` (`tenant_id`,`in_review_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商准入单（一个供应商同时仅一个 IN_REVIEW；APPROVED 必须人工终决）';

CREATE TABLE IF NOT EXISTS `t_proc_risk_alert` (
  `id`                  BIGINT          NOT NULL COMMENT '风险预警主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `supplier_id`         VARCHAR(64)     NOT NULL COMMENT '供应商 partyId',
  `source`              VARCHAR(64)     NOT NULL COMMENT '预警来源（外部征信/履约/合规/AI 建议）',
  `source_ref`          VARCHAR(128)    NOT NULL COMMENT '来源事件引用（与 source 组成幂等键）',
  `severity`            VARCHAR(32)     NOT NULL COMMENT 'HIGH/MEDIUM/LOW/UNKNOWN（UNKNOWN fail-closed 转人工）',
  `alert_type`          VARCHAR(64)     NOT NULL COMMENT '预警类型',
  `status`              VARCHAR(32)     NOT NULL COMMENT 'OPEN/ACKNOWLEDGED/RESOLVED/DISMISSED',
  `evidence_ref`        VARCHAR(256)    DEFAULT NULL COMMENT '证据引用（不回传 PII 原文）',
  `detected_at`         DATETIME(3)     DEFAULT NULL COMMENT '检出时点 UTC',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_risk_alert_dedup` (`tenant_id`,`supplier_id`,`source`,`source_ref`),
  KEY `idx_proc_risk_alert_status` (`tenant_id`,`status`,`detected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风险预警台账（来源幂等；AI 仅辅助登记，不自动裁决供应商）';

CREATE TABLE IF NOT EXISTS `t_proc_performance_score` (
  `id`                  BIGINT          NOT NULL COMMENT '绩效评分主键（Snowflake）',
  `tenant_id`           BIGINT          NOT NULL COMMENT '租户ID',
  `supplier_id`         VARCHAR(64)     NOT NULL COMMENT '供应商 partyId',
  `period`              VARCHAR(32)     NOT NULL COMMENT '评分周期 YYYY-MM 或 YYYY-Qn',
  `delivery_score`      DECIMAL(9,4)    DEFAULT NULL COMMENT '交付得分',
  `quality_score`       DECIMAL(9,4)    DEFAULT NULL COMMENT '质量得分',
  `cost_score`          DECIMAL(9,4)    DEFAULT NULL COMMENT '成本得分',
  `compliance_score`    DECIMAL(9,4)    DEFAULT NULL COMMENT '合规得分',
  `total_score`         DECIMAL(9,4)    DEFAULT NULL COMMENT '综合得分（权重规则由 owner 冻结，当前不自动授信）',
  `evidence_ref`        VARCHAR(256)    DEFAULT NULL COMMENT '证据引用（可复核）',
  `created_by`          BIGINT          DEFAULT NULL COMMENT '创建人ID',
  `updated_by`          BIGINT          DEFAULT NULL COMMENT '修改人ID',
  `created_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `version`             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本（只追加修正版本）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_proc_performance_score_dedup` (`tenant_id`,`supplier_id`,`period`),
  KEY `idx_proc_performance_score_period` (`tenant_id`,`period`,`total_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='供应商绩效评分（只读投影；权重与自动授信属 owner 红线，未开放）';
