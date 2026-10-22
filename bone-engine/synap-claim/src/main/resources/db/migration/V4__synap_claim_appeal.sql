-- synap-claim 第四批 DDL：A8 申诉（48h 复议）
-- 字段与约束真源：《工程详细设计（落地版）》§9 / §6.6 / §7.6

-- A8 申诉记录（拒赔生效45/结案50 → 申诉中60 → 维持45/重启审核41）
CREATE TABLE appeal_record (
  appeal_id       CHAR(36)      NOT NULL COMMENT '申诉ID',
  appeal_no       VARCHAR(64)   NOT NULL COMMENT '申诉号',
  claim_id        CHAR(36)      NOT NULL COMMENT '案件ID',
  claim_no        VARCHAR(64)   NOT NULL COMMENT '案件号',
  original_status VARCHAR(4)    NOT NULL COMMENT '被申诉原终态: 45拒赔生效/50结案',
  status          VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED' COMMENT 'SUBMITTED/DECIDED',
  submitted_by    VARCHAR(64)   NOT NULL COMMENT '申诉人',
  submitted_at    DATETIME(3)   NOT NULL COMMENT '受理时间(48h 起算点=案件终态时间)',
  case_closed_at  DATETIME(3)   NOT NULL COMMENT '案件进入终态时间(申诉时限起算)',
  reviewed_by     VARCHAR(64)   DEFAULT NULL COMMENT '复议人',
  reviewed_at     DATETIME(3)   DEFAULT NULL COMMENT '复议时间',
  review_result   VARCHAR(16)   DEFAULT NULL COMMENT 'UPHELD维持/REOPEN推翻重启',
  review_reason   VARCHAR(512)  DEFAULT NULL COMMENT '复议理由(受控原因码, 禁自由文本)',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (appeal_id),
  UNIQUE KEY uk_appeal_no (tenant_id, appeal_no),
  KEY idx_claim (claim_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '申诉记录';
