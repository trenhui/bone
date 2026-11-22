-- ============================================================
-- 0139_synap_claim_report_case_split.sql
--
-- 一句话：把「报案 = 赔案」（1:1）升级为「报案 1 : 0..N 赔案」，新增 t_claim_report
--        并把存量赔案回填为各自一条报案。
--
-- 背景（为什么必须做）
-- ----------------------------------------------------------------
-- 按 JR/T 0212-2020《财产保险业务要素数据规范》等权威口径，报案（FNOL，事故告知事件）
-- 与赔案（赔付处理单元）是两个聚合：一次告知可以对应 0..N 笔赔付（分案、补赔、二次手术，
-- 或一次事故多张发票分期提交）。旧模型把两者压成一张表，在物理层就排除了「同一起事故
-- 追加第二笔赔案」的可能——而设计文档早就埋了伏笔（batch_no / is_copy），
-- 说明分案需求迟早出现，届时只能靠重建表解决。
--
-- 本次升级把口径落成结构：t_claim_report 是事故维度（保单/计划/被保人/出险时间/计划版本），
-- t_claim_case 是赔付维度，通过 report_id 单向引用报案（赔案不反向持有子集合，
-- 见 design.md D1）。
--
-- 编号
-- ----------------------------------------------------------------
-- 报案号 report_no 与赔案号 claim_no 共用同一编解码内核（BizNoCodec：雪花 ID 6 轮 Feistel
-- 双射 → Crockford Base32(13) → Luhn mod 32 校验位），仅前缀不同（RP / CL）。
-- 前缀不参与校验位计算——这是刻意设计：存量回填时只要把 CL 改写成 RP、其余 22 位原样保留，
-- 校验位依然合法，于是回填可以是纯 SQL，无需在迁移里重算 Feistel（那要求把密钥搬进 DDL）。
--
-- 口径
-- ----------------------------------------------------------------
--   §1 前置形状断言：t_claim_report 若已存在，必须是本合同形态（同名异形表不许静默跳过）
--   §2 建表 t_claim_report（IF NOT EXISTS）
--   §3 建表后零行形状断言（无条件，fail-closed）
--   §4 t_claim_case 加 report_id 列（幂等，先允许 NULL）
--   §5 回填报案行：id = claim.id，report_no = CONCAT('RP', SUBSTRING(claim_no, 3))
--   §6 回填 report_id：report_id = claim.id（旧模型一一对应）
--   §7 收紧 report_id 为 NOT NULL（幂等）
--   §8 索引 idx_tenant_report (tenant_id, report_id)（幂等）
--   §9 纠正 batch_no 列注释（旧注释说它是「报案号前缀」，与拆分后语义不符）
--   §10 终态断言（6 条，全部可幂等重跑）
--
-- 关联（必须同提交）
-- ----------------------------------------------------------------
--   · sql/bone-init.sql：全量基线同批加表与列
--   · db/migration/V17__synap_claim_report.sql：模块参考态同批（保持同源）
--   · ClaimReport.java / ClaimCase.java：实体加 report_id
--   · ClaimReportRepository.findByReportNo / ClaimCaseRepository.findByReportId
--   · doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md §9.3.1/§9.5
--
-- 已知边界（刻意不做）
-- ----------------------------------------------------------------
--   · 不校验回填出的 report_no 是否满足 24 字符新格式：格式真源在 ReportNoGenerator，
--     把 Feistel 搬进 SQL 会形成第二套实现。上线前的试验数据（claim_no 为旧的
--     CL+yyyyMMdd+雪花十进制 28 位形态）回填后长度随之非标准，但「前缀已换、载荷未动」
--     仍然自洽可读；正式上线环境无存量数据，本分支不触发。
--   · 不删任何存量行：回填只新增，赔案行原样保留。
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0139_synap_claim_report_case_split.sql
-- ============================================================

-- ---------- §1 前置形状断言：已存在的 t_claim_report 必须是本合同形态 ----------
-- 为什么需要：CREATE TABLE IF NOT EXISTS 对**同名但形状不符**的存量表是静默 no-op，
-- 旧形态会被当作已就绪，直到运行期才以 Unknown column 炸开。这里把形状问题前移到部署期。
-- 中止后的人工处置（备份优先，绝不删数据）：
--   RENAME TABLE t_claim_report TO t_claim_report_legacy_<yyyymmdd>;
-- 然后重跑本脚本。
SET @has_report_tbl = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report'
);
SET @probe_shape = IF(
  @has_report_tbl > 0,
  'SELECT report_no, policy_no, plan_id, insured_id_hash, claim_type, out_insure_time, plan_version, status FROM t_claim_report LIMIT 0',
  'DO 0'
);
PREPARE probe_shape_stmt FROM @probe_shape;
EXECUTE probe_shape_stmt;
DEALLOCATE PREPARE probe_shape_stmt;

-- ---------- §2 建表 ----------
-- 报案是事故维度聚合根：同一被保人同一次出险的全部赔付共享这一行的上下文。
-- plan_version 放在报案而不是赔案：计划版本在「受理」这一刻锁定，赔案各存一份会让
-- 同一事故的多个赔案锁到不同版本，复算时就分不清哪个才是当时的合同状态。
CREATE TABLE IF NOT EXISTS t_claim_report (
  id              BIGINT        NOT NULL COMMENT '主键(分布式ID)',
  report_no       VARCHAR(64)   NOT NULL COMMENT '报案号(RP+yyyyMMdd+13位Base32+校验位, 24字符, 全局唯一)',
  policy_no       VARCHAR(32)   NOT NULL COMMENT '保单号(事故归属, 追加赔案时继承)',
  plan_id         BIGINT        NOT NULL COMMENT '计划ID(理赔口径基线)',
  insured_id_hash CHAR(64)      NOT NULL COMMENT '假名化被保人(服务端 HMAC 计算, 不落明文)',
  claim_type      VARCHAR(32)   NOT NULL DEFAULT 'OUTPATIENT' COMMENT '险种(事故级属性, 继承给每个赔案)',
  out_insure_time DATETIME(3)   NOT NULL COMMENT '出险时间(事故告知事实)',
  plan_version    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '受理时锁定的计划版本(全案复算确定性)',
  source_channel  VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '渠道',
  source_system   VARCHAR(32)   NOT NULL DEFAULT 'self' COMMENT '来源系统(self/pk_tpa)',
  status          VARCHAR(32)   NOT NULL DEFAULT 'REPORTED' COMMENT '报案状态(REPORTED可追加/CLOSED已关闭)',
  tenant_id       BIGINT        NOT NULL COMMENT '租户',
  created_by      BIGINT        DEFAULT NULL COMMENT '创建人ID',
  created_at      DATETIME(3)   NOT NULL COMMENT '创建时间',
  updated_by      BIGINT        DEFAULT NULL COMMENT '修改人ID',
  updated_at      DATETIME(3)   NOT NULL COMMENT '更新时间',
  version         BIGINT        NOT NULL DEFAULT 0 COMMENT '乐观锁版本(SDK @Version 维护)',
  deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '软删标记',
  PRIMARY KEY (id),
  -- 与 t_claim_case.uk_claim_no 同口径：编号全局唯一，而不是租户内唯一。
  -- 编号源自全局雪花 ID，租户内唯一会掩盖生成器出错（workerId 撞号、外部导入重号）。
  UNIQUE KEY uk_report_no (report_no),
  KEY idx_tenant_report_status (tenant_id, status, created_at),
  KEY idx_tenant_report_policy (tenant_id, policy_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报案主表(FNOL, 1:0..N 的 1 侧)';

-- ---------- §3 建表后形状断言（无条件，此时表必然存在） ----------
SELECT report_no, policy_no, plan_id, insured_id_hash, claim_type, out_insure_time, plan_version, status
FROM t_claim_report
LIMIT 0;

-- ---------- §4 t_claim_case 加 report_id ----------
SET @has_report_id_col = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND column_name = 'report_id'
);
SET @add_report_id = IF(
  @has_report_id_col = 0,
  'ALTER TABLE t_claim_case ADD COLUMN report_id BIGINT NULL COMMENT ''所属报案ID(t_claim_report.id, 1:0..N 的 N 侧)'' AFTER claim_no',
  'DO 0'
);
PREPARE add_report_id_stmt FROM @add_report_id;
EXECUTE add_report_id_stmt;
DEALLOCATE PREPARE add_report_id_stmt;

-- ---------- §5 回填报案行（幂等：按 id 去重，重跑不重复插） ----------
-- report_no = 'RP' + claim_no 去掉 'CL' 前缀后的 22 位载荷与校验位。
-- 校验位不含前缀 ⇒ 前缀改写后仍然合法，无需重算 Feistel。
-- status：已进入终态（closed_at 非空）的赔案对应的报案置 CLOSED——它不会再被追加；
--         其余置 REPORTED，允许在原案上追加后续赔付。
INSERT INTO t_claim_report (
  id, report_no, policy_no, plan_id, insured_id_hash, claim_type, out_insure_time,
  plan_version, source_channel, source_system, status,
  tenant_id, created_by, created_at, updated_by, updated_at, version, deleted
)
SELECT
  c.id,
  CONCAT('RP', SUBSTRING(c.claim_no, 3)),
  c.policy_no,
  c.plan_id,
  c.insured_id_hash,
  c.claim_type,
  c.out_insure_time,
  c.plan_version,
  c.source_channel,
  c.source_system,
  CASE WHEN c.closed_at IS NULL THEN 'REPORTED' ELSE 'CLOSED' END,
  c.tenant_id,
  c.created_by,
  c.created_at,
  c.updated_by,
  c.updated_at,
  0,
  c.deleted
FROM t_claim_case c
WHERE NOT EXISTS (SELECT 1 FROM t_claim_report r WHERE r.id = c.id);

-- ---------- §6 回填 report_id（幂等：只填 NULL 行） ----------
UPDATE t_claim_case SET report_id = id WHERE report_id IS NULL;

-- ---------- §7 收紧 report_id 为 NOT NULL（幂等） ----------
-- 先做一次 fail-closed 探测：仍有 NULL 说明 §6 没覆盖全（例如中途插入了无主赔案），
-- 此时收紧 DDL 会在 MySQL 侧报 1138，但错误信息不含定位线索，故自行 SIGNAL。
SET @null_report_id = (SELECT COUNT(*) FROM t_claim_case WHERE report_id IS NULL);
SET @probe_null = IF(
  @null_report_id = 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 ABORT: t_claim_case.report_id still NULL; run section 6 again'''
);
PREPARE probe_null_stmt FROM @probe_null;
EXECUTE probe_null_stmt;
DEALLOCATE PREPARE probe_null_stmt;

ALTER TABLE t_claim_case
  MODIFY COLUMN report_id BIGINT NOT NULL COMMENT '所属报案ID(t_claim_report.id, 1:0..N 的 N 侧)';

-- ---------- §8 索引（幂等） ----------
-- 读侧主路径是「按报案查同案赔案」，必须走 (tenant_id, report_id)。
-- 注意不叫 idx_report_id：本仓既有环境可能已按历史命名建过同名索引，重名改名的收益为零、风险非零。
SET @has_report_idx = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'idx_tenant_report'
);
SET @add_report_idx = IF(
  @has_report_idx = 0,
  'ALTER TABLE t_claim_case ADD KEY idx_tenant_report (tenant_id, report_id)',
  'DO 0'
);
PREPARE add_report_idx_stmt FROM @add_report_idx;
EXECUTE add_report_idx_stmt;
DEALLOCATE PREPARE add_report_idx_stmt;

-- ---------- §9 纠正 batch_no 列注释 ----------
-- 旧注释写「批次号(报案号前缀+复制赔案分组)」——拆分后 batch_no 不再参与任何编号生成：
-- 报案号由 ClaimReport 自己的雪花 ID 生成，claim_no 由赔案自己的雪花 ID 生成；
-- batch_no 只剩两个用途：OSS 归档路径前缀、复制赔案的分组标识。注释不纠正会成为
-- 下一个人「从 batch_no 推导编号」的诱因。
ALTER TABLE t_claim_case
  MODIFY COLUMN batch_no VARCHAR(64) NOT NULL DEFAULT ''
    COMMENT '批次号(OSS 路径前缀 + 复制赔案分组, 不参与编号生成)';

-- ---------- §10 终态断言（fail-closed：不满足则中止；全部可幂等重跑） ----------
-- 10.1 不存在无主赔案
SET @orphan_case = (SELECT COUNT(*) FROM t_claim_case WHERE report_id IS NULL);
SET @assert_orphan = IF(
  @orphan_case = 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: claim case without report_id exists'''
);
PREPARE assert_orphan_stmt FROM @assert_orphan;
EXECUTE assert_orphan_stmt;
DEALLOCATE PREPARE assert_orphan_stmt;

-- 10.2 不存在指向空气的 report_id（回填完整性）
SET @dangling = (
  SELECT COUNT(*) FROM t_claim_case c
  LEFT JOIN t_claim_report r ON r.id = c.report_id
  WHERE r.id IS NULL
);
SET @assert_dangling = IF(
  @dangling = 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: dangling report_id, no matching t_claim_report'''
);
PREPARE assert_dangling_stmt FROM @assert_dangling;
EXECUTE assert_dangling_stmt;
DEALLOCATE PREPARE assert_dangling_stmt;

-- 10.3 report_id 列已收敛为 NOT NULL
SET @report_id_nullable = (
  SELECT is_nullable FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND column_name = 'report_id'
);
SET @assert_not_null = IF(
  @report_id_nullable = 'NO',
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: report_id column still nullable'''
);
PREPARE assert_not_null_stmt FROM @assert_not_null;
EXECUTE assert_not_null_stmt;
DEALLOCATE PREPARE assert_not_null_stmt;

-- 10.4 报案号全局唯一键存在且列序严格为单列 report_no
SET @report_no_cols = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report'
    AND index_name = 'uk_report_no' AND non_unique = 0
);
SET @assert_uk = IF(
  @report_no_cols = 'report_no',
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: uk_report_no missing or wrong columns'''
);
PREPARE assert_uk_stmt FROM @assert_uk;
EXECUTE assert_uk_stmt;
DEALLOCATE PREPARE assert_uk_stmt;

-- 10.5 报案号无重复（唯一键已保证，双保险）
SET @dup_report_no = (
  SELECT COUNT(*) FROM (
    SELECT report_no FROM t_claim_report GROUP BY report_no HAVING COUNT(*) > 1
  ) AS d
);
SET @assert_dup = IF(
  @dup_report_no = 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: duplicate report_no exists'''
);
PREPARE assert_dup_stmt FROM @assert_dup;
EXECUTE assert_dup_stmt;
DEALLOCATE PREPARE assert_dup_stmt;

-- 10.6 所有报案号带 RP 前缀（前缀是「这是报案号」的唯一标识，丢了就与赔案号混淆）
SET @bad_prefix = (SELECT COUNT(*) FROM t_claim_report WHERE report_no NOT LIKE 'RP%');
SET @assert_prefix = IF(
  @bad_prefix = 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: report_no without RP prefix found'''
);
PREPARE assert_prefix_stmt FROM @assert_prefix;
EXECUTE assert_prefix_stmt;
DEALLOCATE PREPARE assert_prefix_stmt;

SELECT '0139 OK: t_claim_report 就位, 存量赔案已回填为 1:0..N 结构' AS result;
