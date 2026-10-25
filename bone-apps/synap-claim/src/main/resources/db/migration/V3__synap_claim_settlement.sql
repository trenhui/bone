-- synap-claim 第三批 DDL：A7 资金（结算单 + 追加式资金流水）
-- 字段与约束真源：《工程详细设计（落地版）》§9.3/§9.5
-- settle_funding_event 为 append-only（§9.5 注入矩阵：仅 INSERT/SELECT），无 created_by/updated_by/version/deleted。

-- A7 结算单（资金 Saga 编排者，状态机唯一推进方）
CREATE TABLE settle_order (
  id   BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  claim_id        BIGINT      NOT NULL COMMENT '案件ID',
  claim_no        VARCHAR(64)   NOT NULL COMMENT '案件号(幂等键组成)',
  settlement_no   VARCHAR(64)   NOT NULL COMMENT '结算单号(业务号, 与物理 id 分离)',
  amount          DECIMAL(12,2) NOT NULL COMMENT '结算金额',
  funding_stage   VARCHAR(16)   NOT NULL DEFAULT 'SOFT_RESERVED' COMMENT 'SOFT_RESERVED/HARD_RESERVED/REVIEW_FREEZE/PAID/RELEASED/FAILED/REFUNDED',
  account_type    VARCHAR(16)   NOT NULL DEFAULT 'NO_ACCOUNT' COMMENT 'NO_ACCOUNT/PUBLIC/PERSONAL(分账)',
  attempt         INT           NOT NULL DEFAULT 1 COMMENT '占额周期序号({claimNo}:{stage}:{attempt})',
  paid_at         DATETIME(3)   DEFAULT NULL COMMENT '支付时间',
  status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/CLOSED',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_settlement_no (tenant_id, settlement_no),
  KEY idx_claim (claim_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '结算单';

-- A7 资金流水（追加式不可变，审计与三向对账事实源）
CREATE TABLE settle_funding_event (
  id        BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  settlement_id   BIGINT      NOT NULL COMMENT '结算单ID(级联外键)',
  claim_id        BIGINT      NOT NULL COMMENT '案件ID',
  settlement_no   VARCHAR(64)   NOT NULL COMMENT '结算单号',
  stage           VARCHAR(16)   NOT NULL COMMENT '资金状态',
  amount          DECIMAL(12,2) NOT NULL COMMENT '金额',
  operator        VARCHAR(64)   DEFAULT '' COMMENT '操作人',
  account_type    VARCHAR(16)   NOT NULL DEFAULT 'NO_ACCOUNT' COMMENT '账户类型',
  idempotency_key VARCHAR(64)   NOT NULL COMMENT '幂等键 {claimNo}:{stage}:{attempt}',
  event_time      DATETIME(3)   NOT NULL COMMENT '发生时间',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_at      DATETIME(3)   NOT NULL COMMENT '落库时间',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at    DATETIME(3)  NOT NULL          COMMENT '更新时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_idem (tenant_id, idempotency_key),
  KEY idx_settlement (settlement_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '资金流水(追加式)';
