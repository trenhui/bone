-- =============================================================================
-- 0058_synap_claim_tables.sql
--
-- 目的（2026-10-25 联调收尾）：synap-claim 理赔模块 13 张表的可部署 DDL。
--   字段真源：bone-apps/synap-claim/src/main/resources/db/migration/V1-V5
--   （与《工程详细设计（落地版）》§9.3/§9.5 对齐，_check_ddl.py 门禁同源）。
--   该模块此前只有库内迁移文件、无部署入口——服务无启动类亦无建表路径，
--   联调发现理赔链路整体不可运行；本迁移补齐部署侧，使 /api/v1/claims 链路可起。
--
-- 设计
-- ----------------------
-- · 通用列（tenant_id/created_*/updated_*/deleted/version）已在 V1-V5 逐表带齐
--   （5 张无 version 表的实体均未声明 @Version，与 SDK 映射口径一致），本文件原样合并；
-- · 幂等：CREATE TABLE 一律 IF NOT EXISTS，可重复执行；列注释与 V1-V5 保持一字不差；
-- · 与 bone-init.sql 的关系：bone-init.sql 是全量新装真源，本文件是在途库增量迁移
--   （scripts/migration/ 编号惯例）；新装环境由 bone-init.sql 覆盖（见 0060 合并说明）。
--
-- 执行（本地/测试库直接应用；生产 DDL 属 L3，大表须 DBA 走 gh-ost/pt-osc）：
--       mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0058_synap_claim_tables.sql
-- =============================================================================

USE bone;

-- ── 0. 清理早期原型残留表（2026-10-25 联调核验后处置） ───────────────────────
--    仅清理**废弃表名** t_claim_case_item（10 列，早期原型；现行名为 t_claim_item）：
--    该表 0 行、无任何现行代码引用，故 DROP 可承受。
--
--    刻意**不** DROP t_claim_case：它是现行主表。早期原型形态（26 列，含已废弃的
--    insured_name 明文 PII、hospital_level、evidences、fraud_flags）与现行 37 列同名，
--    脚本无法区分二者；一旦在已有真实数据的库上执行，等于删库。改为下方 §0.2 的
--    形状断言（fail-closed）：形状不符即中止，交人工 RENAME 备份后重建。
--    0047_ui_schema_demo_seed.sql 仅在 meta_entity 目录登记旧 demo 实体名，非物理 DDL；
--    e2e 脚本用的是运行时动态表（t_claim_case_e2e_*），均不受影响。
DROP TABLE IF EXISTS t_claim_case_item;

-- ── 0.1 幂等快照表（Idempotency-Key 落库；ClaimCaseController#submit 依赖） ───────────
--   与 bp_idempotency_record 同构（SDK Instant 语义 + (tenant_id, scope_key) 唯一键）；
--   由 infrastructure/idempotency/IdempotencyStoreAdapter 经 SDK 仓储读写。
CREATE TABLE IF NOT EXISTS claim_idempotency_record (
  id                BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  tenant_id         BIGINT        NOT NULL DEFAULT 0 COMMENT '租户ID',
  scope_key         VARCHAR(255)  NOT NULL COMMENT '作用域键：租户|用户|幂等键|方法|路径',
  request_fingerprint VARCHAR(64) NOT NULL COMMENT '请求载荷指纹(SHA-256)',
  snapshot_json     JSON          NOT NULL COMMENT '响应快照(状态码/Location/响应体)',
  expires_at        DATETIME(3)   NOT NULL COMMENT '过期时间(TTL 24h,过后同一键可复用)',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_idem_tenant_scope (tenant_id, scope_key),
  KEY idx_claim_idem_expires_at (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '理赔幂等请求快照(Idempotency-Key)';

-- ---------- V1__synap_claim_init.sql ----------
-- synap-claim 初始化 DDL（MySQL 8.0 / TiDB 同构）
-- 字段与约束真源：doc/ai-product/synapClaim/《工程详细设计（落地版）》§9.3/§9.5（本期实现 §9.3 核心列子集）
-- 通用列约定（§9.2）：全部表带 tenant_id / created_by / created_at / updated_by / updated_at / version / deleted
-- （TenantAbstractEntity 框架注入）；DDL 由部署脚本统一追加通用列，此处仅列业务列。

CREATE TABLE IF NOT EXISTS t_claim_case (
  id          BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  claim_no          VARCHAR(64)   NOT NULL COMMENT '报案号(CL+yyyyMMdd+id, 全行唯一)',
  idempotency_key   VARCHAR(64)   NOT NULL COMMENT '写接口幂等键(Idempotency-Key 请求头值)',
  policy_no         VARCHAR(32)   NOT NULL COMMENT '保单号',
  plan_id           BIGINT        NOT NULL COMMENT '计划ID',
  insured_id_hash   CHAR(64)      NOT NULL COMMENT '假名化被保人(服务端 HMAC 计算)',
  claim_type        VARCHAR(32)   NOT NULL DEFAULT 'OUTPATIENT' COMMENT '险种(一期仅门诊)',
  status            VARCHAR(32)   NOT NULL COMMENT '状态码(枚举name; pk-tpa数字码见代码 ClaimStatus#code)',
  status_sub        VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '子状态(如录入中 22+sub 1普康/2外包)',
  stage             VARCHAR(32)   NOT NULL COMMENT '业务阶段码(8段, 与status正交)',
  hang_up_status    TINYINT       NOT NULL DEFAULT 0 COMMENT '0未挂起/1已挂起(正交机制,不改状态码)',
  hang_up_type      VARCHAR(32)   DEFAULT NULL COMMENT '挂起类型(EVIDENCE/INVESTIGATION/TIMEOUT/FRAUD)',
  invoice_code      VARCHAR(32)   DEFAULT NULL COMMENT '发票代码(草稿未附票为NULL，NULL不占用唯一键)',
  invoice_no        VARCHAR(64)   DEFAULT NULL COMMENT '发票号码(与代码组合防重复赔付，NULL不占用唯一键)',
  out_insure_time   DATETIME(3)   NOT NULL COMMENT '出险时间',
  source_channel    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '渠道',
  source_system     VARCHAR(32)   NOT NULL DEFAULT 'self' COMMENT '来源系统(self/pk_tpa)',
  batch_no          VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '批次号(报案号前缀+复制赔案分组)',
  claim_amount      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '申请金额',
  approved_amount   DECIMAL(12,2) DEFAULT NULL COMMENT '核定金额(理算后回填)',
  first_reviewer_id VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '第一审核人(核赔)',
  second_reviewer_id VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '第二复核人(大额>=1万双人复核)',
  reject_comment    VARCHAR(2000) NOT NULL DEFAULT '' COMMENT '拒赔五要素(REJECTED必填)',
  reject_type       VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '受控拒赔原因码(01-13+20/30/40)',
  plan_version      VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '受理时锁定的计划版本(复算确定性)',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  closed_at         DATETIME(3)   DEFAULT NULL COMMENT '进入终态时间(申诉48h时限起算点)',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本(SDK @Version 维护)',
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_claim_no (tenant_id, claim_no),
  UNIQUE KEY uk_tenant_idem (tenant_id, idempotency_key),
  -- 发票唯一性硬约束（R-FRAUD-07）：含 deleted，软删案件不永久占用发票键；
  -- 发票列为 NULL（草稿未附票）时按 SQL 唯一索引语义互不相等，多条草稿可共存
  UNIQUE KEY uk_tenant_invoice (tenant_id, invoice_code, invoice_no, deleted),
  KEY idx_tenant_status (tenant_id, status, created_at),
  KEY idx_tenant_insured (tenant_id, insured_id_hash),
  KEY idx_policy_no (policy_no),
  KEY idx_tenant_batch (tenant_id, batch_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '赔案主表';

-- ── 0.2 主表形状断言（fail-closed，替代原先的 DROP） ─────────────────────────
--    为什么需要：CREATE TABLE IF NOT EXISTS 对**同名但形状不符**的存量表是静默 no-op，
--    于是旧 26 列原型会被当作已就绪，直到运行期才以 Unknown column / 唯一键缺失炸开。
--    这里用「零行探测」把形状问题前移到部署期：
--      · 新装库 / 已是 37 列 → 0 行返回，no-op；
--      · 旧 26 列原型        → MySQL 报 Unknown column 'idempotency_key'，脚本中止。
--    中止后的人工处置（备份优先，绝不删数据）：
--        RENAME TABLE t_claim_case TO t_claim_case_legacy_20261025;
--    然后重跑本脚本。
SELECT idempotency_key FROM t_claim_case LIMIT 0;

CREATE TABLE IF NOT EXISTS t_claim_item (
  id          BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  claim_id         BIGINT      NOT NULL COMMENT '案件ID',
  item_name        VARCHAR(128)  NOT NULL COMMENT '项目名称',
  item_code        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '医保项目/药品编码(目录编码匹配唯一键)',
  category         VARCHAR(32)   NOT NULL COMMENT 'DRUG/SERVICE/EXAM/MATERIAL',
  quantity         DECIMAL(10,2) NOT NULL DEFAULT 1 COMMENT '数量',
  unit_price       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '单价',
  amount           DECIMAL(12,2) NOT NULL COMMENT '金额',
  social_flag      VARCHAR(16)   NOT NULL DEFAULT '' COMMENT 'IN/OUT/SELF 社保内外',
  medical_type     VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '甲类/乙类/丙类',
  -- 医保三段拆分（R-DED-03 免赔口径 / R-RAT-01 赔付比例的唯一数据基础）
  pooling_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '统筹支付金额',
  self_pay_1       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '自付一(医保内个人自付,计入免赔)',
  self_pay_2       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '自付二(乙类先行自付)',
  self_pay_c       DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '丙类自费(目录外,赔付0)',
  third_party_paid DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '第三方已赔付',
  valid_amount     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '合理费用(理算基准)',
  invalid_amount   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '不合理费用',
  tenant_id        BIGINT        NOT NULL COMMENT '租户',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at       DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at       DATETIME(3)   NOT NULL COMMENT '更新时间',
  version          BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  KEY idx_claim (claim_id),
  KEY idx_item_code (tenant_id, item_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '费用明细项';

-- ---------- V2__synap_claim_evidence_adjust_quota.sql ----------
-- synap-claim 第二批 DDL：A2 证据 / A4 责任 / A6 理算 / A5 额度
-- 字段与约束真源：《工程详细设计（落地版）》§9.3/§9.5
-- 通用列（tenant_id / created_by / created_at / updated_by / updated_at / version / deleted）
-- 由 TenantAbstractEntity / AbstractEntity 框架注入（§9.2 + §9.5 注入矩阵）。

-- A2 证据材料
CREATE TABLE IF NOT EXISTS t_claim_evidence (
  id     BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  claim_id        BIGINT      NOT NULL COMMENT '案件ID',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim_content_hash (claim_id, content_hash),
  KEY idx_claim (claim_id),
  KEY idx_tenant_ocr (tenant_id, ocr_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '证据材料';

-- A4 保险责任（版本化：同一 liability_uuid 多版本并存，已生效版本不可改）
CREATE TABLE IF NOT EXISTS t_adj_liability (
  id                BIGINT      NOT NULL COMMENT '主键(分布式ID)',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_uuid_ver (tenant_id, liability_uuid, liability_version),
  KEY idx_plan_status (tenant_id, plan_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '保险责任(版本化)';

-- A6 理算记录
CREATE TABLE IF NOT EXISTS t_adj_calculation (
  id    BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  claim_id          BIGINT      NOT NULL COMMENT '案件ID',
  liability_uuid    VARCHAR(64)   NOT NULL COMMENT '命中责任UUID',
  liability_version VARCHAR(32)   NOT NULL COMMENT '责任版本(复算锁定)',
  rule_version      VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '规则版本',
  formula_snapshot  VARCHAR(4000) NOT NULL COMMENT '公式快照(物化责任参数, 可自包含复算)',
  formula_hash      CHAR(64)      NOT NULL COMMENT '快照哈希(同输入同输出校验)',
  total_amount      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '理算合计',
  result_status     VARCHAR(16)   NOT NULL DEFAULT 'FROZEN' COMMENT 'FROZEN/CONFIRMED/UNFROZEN/ROLLBACKED',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_claim (claim_id, created_at),
  KEY idx_tenant_status (tenant_id, result_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '理算记录';

-- A6 理算明细（归因成分链，拒赔五要素「事实认定」的数据基础）
CREATE TABLE IF NOT EXISTS t_adj_calculation_line (
  id         BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  calculation_id  BIGINT      NOT NULL COMMENT '理算ID',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  KEY idx_calculation (calculation_id),
  KEY idx_zero_reason (tenant_id, zero_reason)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '理算明细(归因成分链)';

-- A5 额度账户（三值模型）
CREATE TABLE IF NOT EXISTS t_quota_account (
  id BIGINT      NOT NULL COMMENT '主键(分布式ID)',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  created_at       DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  updated_at       DATETIME(3)   NOT NULL COMMENT '更新时间',
  version          BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  deleted          TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  UNIQUE KEY uk_quota (tenant_id, insured_id_hash, policy_no, plan_id, quota_year, quota_type, liability_uuid),
  KEY idx_insured (tenant_id, insured_id_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度账户(三值模型)';

-- A5 额度流水（append-only：账户是投影，流水是事实，守恒校验依赖它）
CREATE TABLE IF NOT EXISTS t_quota_transaction (
  id           BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  quota_account_id BIGINT      NOT NULL COMMENT '账户ID',
  claim_id         BIGINT      NOT NULL COMMENT '案件ID',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_at    DATETIME(3)  NOT NULL          COMMENT '更新时间',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_idem (tenant_id, idempotency_key),
  KEY idx_account (quota_account_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '额度流水(append-only)';

-- ---------- V3__synap_claim_settlement.sql ----------
-- synap-claim 第三批 DDL：A7 资金（结算单 + 追加式资金流水）
-- 字段与约束真源：《工程详细设计（落地版）》§9.3/§9.5
-- settle_funding_event 为 append-only（§9.5 注入矩阵：仅 INSERT/SELECT），无 created_by/updated_by/version/deleted。

-- A7 结算单（资金 Saga 编排者，状态机唯一推进方）
CREATE TABLE IF NOT EXISTS settle_order (
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
CREATE TABLE IF NOT EXISTS settle_funding_event (
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

-- ---------- V4__synap_claim_appeal.sql ----------
-- synap-claim 第四批 DDL：A8 申诉（48h 复议）
-- 字段与约束真源：《工程详细设计（落地版）》§9 / §6.6 / §7.6

-- A8 申诉记录（拒赔生效45/结案50 → 申诉中60 → 维持45/重启审核41）
CREATE TABLE IF NOT EXISTS appeal_record (
  id       BIGINT      NOT NULL COMMENT '主键(分布式ID)',
  appeal_no       VARCHAR(64)   NOT NULL COMMENT '申诉号',
  claim_id        BIGINT      NOT NULL COMMENT '案件ID',
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
  created_by    BIGINT       DEFAULT NULL COMMENT '创建人ID',
  updated_by    BIGINT       DEFAULT NULL COMMENT '修改人ID',
  deleted       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_appeal_no (tenant_id, appeal_no),
  KEY idx_claim (claim_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '申诉记录';

-- ---------- V5__synap_claim_decision.sql ----------
-- synap-claim 第五批 DDL：A3 决策执行（决策图一次执行 + 节点轨迹）
-- 字段真源：《工程详细设计（落地版）》§9（claim_decision_execution / claim_decision_trace）
-- 主键列名遵循 bone-metadata-sdk 约定（id，由实体 id 字段推导），业务标识另设独立列。
--
-- 两处对 §9 原稿的修正（原因随列注释登记）：
-- ① active_flag 允许 NULL：原 DDL 用 UNIQUE(claim_id, active_flag) + 0/1 取值，实际会连带限制
--    同案最多 2 行（0 与 1 各一行），无法保留多次执行历史；改 NULL=非活跃后，MySQL 唯一索引
--    只约束非 NULL 值，"每案仅一个活跃执行 + 多条历史"两语义同时成立。
-- ② 执行行非 append-only：需在 RUNNING→SUCCESS/FALLBACK/FAILED 间更新（active_flag 释放、
--    结论落位），故保留 version 乐观锁列与 updated_* 列；轨迹行才是真正的 append-only。

-- 决策图一次执行
CREATE TABLE IF NOT EXISTS claim_decision_execution (
  id                BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  trace_id          VARCHAR(64)   NOT NULL COMMENT '追溯ID(一次执行一个; 回放键)',
  claim_id          BIGINT        NOT NULL COMMENT '案件ID',
  graph_version     VARCHAR(32)   NOT NULL COMMENT '决策图版本(拓扑变更须发版, 禁止热改)',
  rule_version      VARCHAR(32)   NOT NULL COMMENT '规则版本快照',
  model_version     VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '模型版本(AI节点, 回放与复算必需)',
  prompt_version    VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '提示词版本(AI节点)',
  catalog_version   VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '目录版本(核责节点)',
  status            VARCHAR(16)   NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/SUCCESS/FALLBACK/FAILED',
  active_flag       TINYINT(1)    DEFAULT NULL COMMENT '1=活跃执行; 完成后置NULL(唯一约束只约束非NULL)',
  decision_result   VARCHAR(32)   DEFAULT NULL COMMENT 'AUTO_APPROVE/SUGGEST_REJECT/MANUAL_REQUIRED/NEED_MORE_EVIDENCE',
  decision_tier     VARCHAR(16)   DEFAULT NULL COMMENT '内部分档 AUTO/REVIEW/MANUAL(不外露)',
  ai_confidence     DECIMAL(6,4)  DEFAULT NULL COMMENT '有效置信度(AI成功取AI置信度; LLM降级取规则路径置信度)',
  started_at        DATETIME(3)   NOT NULL COMMENT '开始时间',
  finished_at       DATETIME(3)   DEFAULT NULL COMMENT '结束时间',
  duration_ms       INT           DEFAULT NULL COMMENT '耗时',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_at        DATETIME(3)   NOT NULL COMMENT '创建时间',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  version           BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trace (tenant_id, trace_id),
  UNIQUE KEY uk_claim_active (claim_id, active_flag),
  KEY idx_claim (claim_id, started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '决策执行记录';

-- 决策节点轨迹（append-only，不可更新不可删除）
CREATE TABLE IF NOT EXISTS claim_decision_trace (
  id                BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  trace_id          VARCHAR(64)   NOT NULL COMMENT '追溯ID(与执行一致; 按此回放全链)',
  claim_id          BIGINT        NOT NULL COMMENT '案件ID',
  execution_id      BIGINT        NOT NULL COMMENT '执行ID(关联 claim_decision_execution.id)',
  node_seq          INT           NOT NULL COMMENT '节点序号(证据→解析→核责→理算→决策)',
  node_type         VARCHAR(16)   NOT NULL COMMENT 'OCR/RULE/LLM/DECISION(对外4类; 内部节点名见 node_name)',
  node_name         VARCHAR(64)   NOT NULL COMMENT '内部节点名 EvidenceLoad/RuleCheck/AiAssess/Adjust/Arbitrate',
  input_summary     VARCHAR(200)  NOT NULL DEFAULT '' COMMENT '输入摘要(≤200字; 禁落原始PII)',
  output            JSON          DEFAULT NULL COMMENT '节点完整输出',
  action_result     VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '节点结论(受控短码)',
  duration_ms       INT           NOT NULL DEFAULT 0 COMMENT '节点耗时',
  tenant_id         BIGINT        NOT NULL COMMENT '租户',
  created_at        DATETIME(3)   NOT NULL COMMENT '写入时间',
  created_by        BIGINT        DEFAULT NULL COMMENT '创建人ID',
  updated_at        DATETIME(3)   NOT NULL COMMENT '更新时间',
  updated_by        BIGINT        DEFAULT NULL COMMENT '修改人ID',
  deleted           TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_trace_seq (trace_id, node_seq),
  KEY idx_execution (execution_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '决策节点轨迹(append-only)';
