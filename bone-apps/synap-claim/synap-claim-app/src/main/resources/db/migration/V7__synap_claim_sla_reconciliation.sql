-- synap-claim 第七批 DDL：SLA 物化读模型（ADR-003）+ 资金对账报告/差异（§11.7）+ claim_case.limit_hour 列
-- 字段真源：《工程详细设计（落地版）》§9.5 claim_case_sla / reconciliation_report / reconciliation_diff
-- 说明：
--   t_claim_case_sla 为**可变更读模型**（每 20 分钟由 schedule 从 t_claim_case_track 物化，乐观锁 version 防并发刷新），
--   非 append-only，故含 updated_at/updated_by/deleted。
--   t_reconciliation_report / t_reconciliation_diff 为 **append-only**（仅 INSERT，对账结论不可篡改）。

-- 工作日剩余小时（§6.6 时效双轨）：保留列位与 §9.5 目标态一致；
-- 一期由 t_claim_case_sla.remaining_work_hours 承载读模型口径，本列不作为刷新写目标
-- （避免每 20 分钟写 t_claim_case.updated_at 污染超期关单扫描的 updated_at 语义）。
ALTER TABLE t_claim_case
  ADD COLUMN limit_hour DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '工作日时效剩余小时数(§6.6；一期读模型见 t_claim_case_sla)' AFTER hang_up_type;

-- SLA 计时聚合（运营读模型，CQRS/ES 查询侧；由 schedule 每 20 分钟从 t_claim_case_track 物化）
CREATE TABLE t_claim_case_sla (
  id                   BIGINT       NOT NULL COMMENT '快照ID(分布式ID)',
  claim_id             BIGINT       NOT NULL COMMENT '案件ID(租户内唯一)',
  tenant_id            BIGINT       NOT NULL COMMENT '租户',
  current_status       VARCHAR(32)  NOT NULL COMMENT '当前状态码',
  current_stage        VARCHAR(32)  NOT NULL COMMENT '当前阶段码',
  stage_entered_at     DATETIME(3)  NOT NULL COMMENT '进入当前阶段时间(阶段停留计时起点)',
  legal_due_at         DATETIME(3)  DEFAULT NULL COMMENT '法定时限截止(自然日，不可放宽)',
  sla_due_at           DATETIME(3)  DEFAULT NULL COMMENT '内部作业SLA截止(一期近似)',
  remaining_work_hours DECIMAL(8, 2) NOT NULL DEFAULT 0.00 COMMENT '工作日剩余小时(limit_hour口径)',
  hang_accum_seconds   BIGINT       NOT NULL DEFAULT 0 COMMENT '累计挂起秒数(工作日口径)',
  last_refresh_at      DATETIME(3)  NOT NULL COMMENT '最近刷新时间',
  version              BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁(并发刷新)',
  created_at           DATETIME(3)  NOT NULL COMMENT '创建时间',
  updated_at           DATETIME(3)  NOT NULL COMMENT '更新时间',
  created_by           BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_by           BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted              TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim (tenant_id, claim_id),
  KEY idx_due (tenant_id, sla_due_at),
  KEY idx_refresh (last_refresh_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '案件SLA计时聚合(运营读模型)';

-- 资金对账报告（日终，append-only，不可篡改）
CREATE TABLE t_reconciliation_report (
  id             BIGINT        NOT NULL COMMENT '对账报告ID(分布式ID)',
  biz_date       DATE          NOT NULL COMMENT '业务日期(T+n)',
  channel        VARCHAR(32)   NOT NULL COMMENT '对账通道(PAYMENT/QUOTA/TPA)',
  total_count    INT           NOT NULL DEFAULT 0 COMMENT '总笔数',
  balanced_count INT           NOT NULL DEFAULT 0 COMMENT '平账笔数',
  diff_count     INT           NOT NULL DEFAULT 0 COMMENT '差异笔数',
  total_amount   DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '本方合计',
  counter_amount DECIMAL(14, 2) NOT NULL DEFAULT 0.00 COMMENT '对方合计',
  status         VARCHAR(16)   NOT NULL DEFAULT 'BALANCED' COMMENT 'BALANCED/DIFF/FAILED',
  checked_at     DATETIME(3)   NOT NULL COMMENT '对账时间',
  tenant_id      BIGINT        NOT NULL COMMENT '租户(安全隔离+按租户对账依赖)',
  created_at     DATETIME(3)   NOT NULL COMMENT '创建时间',
  created_by     BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at     DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by     BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_date_channel (tenant_id, biz_date, channel)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资金对账报告';

-- 对账差异明细（append-only）
CREATE TABLE t_reconciliation_diff (
  id            BIGINT        NOT NULL COMMENT '差异ID(分布式ID)',
  report_id     BIGINT        NOT NULL COMMENT '对账报告ID',
  tenant_id     BIGINT        NOT NULL COMMENT '租户',
  claim_no      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '报案号',
  settlement_no VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '结算单号',
  diff_type     VARCHAR(32)   NOT NULL COMMENT 'AMOUNT_MISMATCH/STATUS_MISMATCH/MISSING_LOCAL/MISSING_REMOTE/QUOTA_BREAK',
  local_amount  DECIMAL(14, 2) DEFAULT NULL COMMENT '本方金额',
  remote_amount DECIMAL(14, 2) DEFAULT NULL COMMENT '对方金额',
  local_stage   VARCHAR(24)   NOT NULL DEFAULT '' COMMENT '本方资金阶段',
  remote_status VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '对方状态',
  resolution    VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RESOLVED/ADJUSTED/WAIVED',
  resolved_by   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '处置人',
  resolved_at   DATETIME(3)   DEFAULT NULL COMMENT '处置时间',
  created_at    DATETIME(3)   NOT NULL COMMENT '创建时间',
  created_by    BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at    DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by    BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted       TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_report (report_id),
  KEY idx_claim (claim_no),
  KEY idx_resolution (resolution, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资金对账差异明细';
