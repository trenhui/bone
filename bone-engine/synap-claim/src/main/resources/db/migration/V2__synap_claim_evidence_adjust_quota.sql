-- synap-claim 第二批 DDL：A2 证据 / A4 责任 / A6 理算 / A5 额度
-- 字段与约束真源：《工程详细设计（落地版）》§9.3/§9.5
-- 通用列（tenant_id / created_by / created_at / updated_by / updated_at / version / deleted）
-- 由 TenantAbstractEntity / AbstractEntity 框架注入（§9.2 + §9.5 注入矩阵）。

-- A2 证据材料
CREATE TABLE t_claim_evidence (
  evidence_id     CHAR(36)      NOT NULL COMMENT '材料ID',
  claim_id        CHAR(36)      NOT NULL COMMENT '案件ID',
  doc_type        VARCHAR(32)   NOT NULL COMMENT 'INVOICE/LIST/MEDICAL_RECORD/MEDICAL_SETTLEMENT',
  file_ref        VARCHAR(256)  NOT NULL COMMENT '附件引用(平台附件服务, STS 直传)',
  content_hash    CHAR(64)      NOT NULL COMMENT '文件内容SHA256(重复上传判定, §9.5)',
  ocr_status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARSING/DONE/FAILED/MANUAL',
  ocr_confidence  DECIMAL(6,4)  DEFAULT NULL COMMENT '解析置信度(<0.7 转人工补录)',
  verify_valid    VARCHAR(16)   NOT NULL DEFAULT 'UNKNOWN' COMMENT '票面验真(UNKNOWN/PASS/FAIL/UNVERIFIABLE)',
  extracted_json  VARCHAR(4000) NOT NULL DEFAULT '' COMMENT '解析结果(字段+置信度+证据坐标)',
  locked          TINYINT       NOT NULL DEFAULT 0 COMMENT '0未锁定/1已锁定(锁定后不可改)',
  locked_at       DATETIME(3)   DEFAULT NULL COMMENT '锁定时间',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (evidence_id),
  UNIQUE KEY uk_claim_content_hash (claim_id, content_hash),
  KEY idx_claim (claim_id),
  KEY idx_tenant_ocr (tenant_id, ocr_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '证据材料';

-- A4 保险责任（版本化：同一 liability_uuid 多版本并存，已生效版本不可改）
CREATE TABLE t_adj_liability (
  id                CHAR(36)      NOT NULL COMMENT '主键',
  liability_uuid    VARCHAR(64)   NOT NULL COMMENT '责任UUID(版本化主键)',
  liability_version VARCHAR(32)   NOT NULL COMMENT '责任版本',
  plan_id           BIGINT        NOT NULL COMMENT '计划ID',
  liability_code    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '责任编码(R01...)',
  liability_type    VARCHAR(32)   NOT NULL DEFAULT 'REIMBURSEMENT' COMMENT 'REIMBURSEMENT/FIXED_AMOUNT/ALLOWANCE',
  pay_percent       DECIMAL(6,2)  NOT NULL DEFAULT 0 COMMENT '赔付比例(百分数; 缺省0, 禁隐式默认90%)',
  liability_deduct  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '年度免赔额(R-DED-03)',
  liability_limit   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '责任额度上限(R-CAP)',
  social_only       TINYINT       NOT NULL DEFAULT 1 COMMENT '1仅赔社保内(R-DIR-01)',
  waiting_period    INT           NOT NULL DEFAULT 0 COMMENT '等待期(天)',
  status            VARCHAR(16)   NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/GRAY/ACTIVE/RETIRED',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_uuid_ver (tenant_id, liability_uuid, liability_version),
  KEY idx_plan_status (tenant_id, plan_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保险责任(版本化)';

-- A6 理算记录
CREATE TABLE t_adj_calculation (
  calculation_id    CHAR(36)      NOT NULL COMMENT '理算ID',
  claim_id          CHAR(36)      NOT NULL COMMENT '案件ID',
  liability_uuid    VARCHAR(64)   NOT NULL COMMENT '命中责任UUID',
  liability_version VARCHAR(32)   NOT NULL COMMENT '责任版本(复算锁定)',
  rule_version      VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '规则版本',
  formula_snapshot  VARCHAR(4000) NOT NULL COMMENT '公式快照(物化责任参数, 可自包含复算)',
  formula_hash      CHAR(64)      NOT NULL COMMENT '快照哈希(同输入同输出校验)',
  total_amount      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '理算合计',
  result_status     VARCHAR(16)   NOT NULL DEFAULT 'FROZEN' COMMENT 'FROZEN/CONFIRMED/UNFROZEN/ROLLBACKED',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (calculation_id),
  KEY idx_claim (claim_id, created_at),
  KEY idx_tenant_status (tenant_id, result_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '理算记录';

-- A6 理算明细（归因成分链，拒赔五要素「事实认定」的数据基础）
CREATE TABLE t_adj_calculation_line (
  line_id         CHAR(36)      NOT NULL COMMENT '明细ID',
  calculation_id  CHAR(36)      NOT NULL COMMENT '理算ID',
  liability_uuid  VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '责任UUID',
  line_type       VARCHAR(16)   NOT NULL DEFAULT 'OUTPUT' COMMENT 'INPUT/RULE/OUTPUT',
  input_snapshot  VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '输入快照(明细与三段拆分)',
  fee_calculate   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '计算基准',
  fee_deduct      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '免赔扣减',
  ratio_payout    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '按比例应付',
  fee_occupied    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '占额',
  fee_payout      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '实付',
  fee_remain      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '剩余可用额度',
  zero_reason     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '零赔付归因(ZERO_REASONABLE/DEDUCT/LIMIT/PERCENT)',
  reject_type     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '受控拒赔原因码(§8.3)',
  formula_hash    CHAR(64)      NOT NULL DEFAULT '' COMMENT '逐行公式哈希',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (line_id),
  KEY idx_calculation (calculation_id),
  KEY idx_zero_reason (tenant_id, zero_reason)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '理算明细(归因成分链)';

-- A5 额度账户（三值模型）
CREATE TABLE t_quota_account (
  quota_account_id CHAR(36)      NOT NULL COMMENT '账户ID',
  insured_id_hash  CHAR(64)      NOT NULL COMMENT '假名化被保人',
  policy_no        VARCHAR(32)   NOT NULL COMMENT '保单号',
  plan_id          BIGINT        NOT NULL COMMENT '计划ID',
  quota_year       INT           NOT NULL COMMENT '额度年度(按保单年度)',
  quota_type       VARCHAR(32)   NOT NULL DEFAULT 'PERSONAL' COMMENT 'PERSONAL/LIABILITY_CAP/LIABILITY_PERSONAL',
  liability_uuid   VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '责任UUID(LIABILITY_* 维度必填)',
  quota_initial    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '初始额度',
  quota_claimed    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已扣减(理算已确认)',
  quota_frozen     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已冻结(理算未确认)',
  deductible_used  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '年免赔已用(R-DED-03)',
  cap_used         DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '年封顶已用(R-CAP)',
  insured_state    TINYINT       NOT NULL DEFAULT 0 COMMENT '0正常/1减人',
  tenant_id        BIGINT        NOT NULL COMMENT '租户',
  created_by       VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '创建人',
  created_at       DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by       VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '更新人',
  updated_at       DATETIME(3)   NOT NULL COMMENT '更新时间',
  version          BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (quota_account_id),
  UNIQUE KEY uk_quota (tenant_id, insured_id_hash, policy_no, plan_id, quota_year, quota_type, liability_uuid),
  KEY idx_insured (tenant_id, insured_id_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度账户(三值模型)';

-- A5 额度流水（append-only：账户是投影，流水是事实，守恒校验依赖它）
CREATE TABLE t_quota_transaction (
  txn_id           CHAR(36)      NOT NULL COMMENT '流水ID',
  quota_account_id CHAR(36)      NOT NULL COMMENT '账户ID',
  claim_id         CHAR(36)      NOT NULL COMMENT '案件ID',
  settlement_no    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '结算单号',
  direction        VARCHAR(16)   NOT NULL COMMENT 'OCCUPY/ROLLBACK',
  txn_type         VARCHAR(16)   NOT NULL COMMENT 'SOFT/HARD/PAID/RELEASE',
  amount           DECIMAL(12,2) NOT NULL COMMENT '金额',
  before_amount    DECIMAL(12,2) NOT NULL COMMENT '变动前',
  after_amount     DECIMAL(12,2) NOT NULL COMMENT '变动后',
  idempotency_key  VARCHAR(64)   NOT NULL COMMENT '幂等键 {claimNo}:{stage}:{attempt}',
  occurred_at      DATETIME(3)   NOT NULL COMMENT '发生时间',
  tenant_id        BIGINT        NOT NULL COMMENT '租户',
  created_at       DATETIME(3)   NOT NULL COMMENT '落库时间',
  PRIMARY KEY (txn_id),
  UNIQUE KEY uk_tenant_idem (tenant_id, idempotency_key),
  KEY idx_account (quota_account_id, txn_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度流水(append-only)';
