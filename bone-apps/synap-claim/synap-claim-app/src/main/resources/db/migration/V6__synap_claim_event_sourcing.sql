-- ADR-003（事件溯源 / 状态流转流水）+ ADR-008（领域事件发件箱，可靠投递兜底）
-- 审计列 tenant_id/created_by/created_at/updated_by/updated_at/deleted 由 SDK 实体基类（TenantAbstractEntity）统一注入；
-- t_claim_case_track 为 append-only（仅 INSERT，状态机跃迁经 ClaimStatusChangedEvent 事件驱动落库）；
-- t_domain_event_outbox 由 OutboxRelayJob（adapter.schedule，ADR-0030 跨租户扫描）派发后刷 status（乐观锁 version 防并发重发）。

CREATE TABLE t_claim_case_track (
  id           BIGINT       NOT NULL COMMENT '流水ID(分布式ID)',
  claim_id     BIGINT       NOT NULL COMMENT '案件ID',
  from_status  VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '跃迁前状态码',
  to_status    VARCHAR(32)  NOT NULL COMMENT '跃迁后状态码',
  stage        VARCHAR(32)  NOT NULL COMMENT '阶段码(SUBMIT/REVIEW/SETTLE/CLOSE/APPEAL)',
  action_code  VARCHAR(64)  NOT NULL COMMENT '触发动作',
  operator_id  VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '操作人ID',
  operator_name VARCHAR(64) NOT NULL DEFAULT '' COMMENT '操作人名',
  remark       VARCHAR(512) NOT NULL DEFAULT '' COMMENT '备注',
  created_at   DATETIME(3)  NOT NULL COMMENT '发生时间(由基类审计列注入)',
  tenant_id    BIGINT       NOT NULL COMMENT '租户(安全隔离 + 按租户对账依赖)',
  created_by   BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at   DATETIME(3)  NOT NULL COMMENT '更新时间',
  updated_by   BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_claim_time (claim_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '案件状态流转流水(append-only，ADR-003)';

CREATE TABLE t_domain_event_outbox (
  id            BIGINT       NOT NULL COMMENT '事件ID(分布式ID)',
  tenant_id     BIGINT       NOT NULL COMMENT '租户(安全隔离 + 中继按租户扫描)',
  aggregate     VARCHAR(32)  NOT NULL COMMENT '聚合类型(ClaimCase/Settlement/...)',
  aggregate_id  BIGINT       NOT NULL COMMENT '聚合ID',
  event_type    VARCHAR(64)  NOT NULL COMMENT '事件类型(ClaimStatusChanged/...)',
  payload       JSON         NOT NULL COMMENT '事件载荷(JSON)',
  status        VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENT/FAILED',
  retry_count   INT          NOT NULL DEFAULT 0 COMMENT '投递重试次数',
  next_retry_at DATETIME(3)  DEFAULT NULL COMMENT '下次重试时间',
  created_at    DATETIME(3)  NOT NULL COMMENT '创建时间',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version       BIGINT       NOT NULL DEFAULT 0 COMMENT '乐观锁(中继刷 status 防并发重发)',
  PRIMARY KEY (id),
  KEY idx_tenant_status (tenant_id, status, next_retry_at),
  KEY idx_aggregate (tenant_id, aggregate, aggregate_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '领域事件发件箱(可靠投递兜底，ADR-008)';
