-- ============================================================
-- 0139_synap_claim_report_one_to_many.sql
--
-- 一句话：落地报案 1:N 赔案模型——新建报案表 t_claim_report，
--        并给 t_claim_case 增加 report_id / report_no 归属列与索引。
--
-- 背景（为什么必须做）
-- ----------------------------------------------------------------
-- 业界最佳实践（JR/T 0212-2020《财产保险业务要素数据规范》把报案/立案/赔案列为独立实体）：
-- 报案 = 一次事故告知事件（FNOL），赔案 = 一笔赔付处理单元，一个报案下可有 N 个赔案
-- （多发票分案 / 多责任分案 / 后续追加索赔）。原实现是彻底的 1:1——无报案表，
-- claim_no 挂在赔案上兼作报案号，一次事故多张发票只能靠 batchNo 分组、无法独立理算结案。
--
-- 本迁移把报案建模为独立实体：
--   · t_claim_report：报案主表，report_no 全局唯一（RP 前缀）；
--   · t_claim_case：新增 report_id（归属报案物理主键）+ report_no（冗余展示键）。
--
-- 系统未上线、无存量数据，改造零兼容负担。report_id/report_no 允许 NULL（历史/旁路数据），
-- 正常提交均在同一事务内绑定报案。资金幂等键仍以 claim_no 为单位，不受分案影响。
--
-- 口径
-- ----------------------------------------------------------------
--   · 建表 t_claim_report（幂等：information_schema.tables 判定）；
--   · t_claim_case 加两列（幂等：information_schema.columns 判定）+ 两个索引；
--   · 终态断言：表存在、列齐全、索引就位、唯一键列序正确。
--
-- 关联（必须同提交）
-- ----------------------------------------------------------------
--   · ClaimReport.java / ReportNoGenerator.java / OpaqueNoCodec.java：报案聚合与编号
--   · ClaimCase.java：reportId/reportNo 字段 + assignReport/splitFrom
--   · bone-init.sql + db/migration/V1：全量基线与参考态同批
--   · migration-manifest.json：登记 order=145
--   · doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md §9.3.1
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0139_synap_claim_report_one_to_many.sql
-- ============================================================

-- ---------- 阶段 1：建报案表（幂等） ----------
SET @has_report_table = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report'
);
SET @create_report = IF(@has_report_table = 0,
  'CREATE TABLE t_claim_report (
     id                BIGINT        NOT NULL COMMENT ''主键(雪花)'',
     report_no         VARCHAR(64)   NOT NULL COMMENT ''报案号(RP+yyyyMMdd+13位Base32+校验位, 24字符, 全局唯一)'',
     idempotency_key   VARCHAR(64)   NOT NULL COMMENT ''报案登记幂等键(Idempotency-Key)'',
     policy_no         VARCHAR(32)   NOT NULL COMMENT ''归属保单号'',
     insured_id_hash   CHAR(64)      NOT NULL COMMENT ''假名化被保人'',
     out_insure_time   DATETIME(3)   NOT NULL COMMENT ''出险时间(事故告知事实)'',
     loss_description  VARCHAR(500)  DEFAULT NULL COMMENT ''事故简述(PII不落明文)'',
     source_channel    VARCHAR(32)   NOT NULL DEFAULT '''' COMMENT ''渠道'',
     source_system     VARCHAR(32)   NOT NULL DEFAULT ''self'' COMMENT ''来源系统'',
     status            VARCHAR(32)   NOT NULL DEFAULT ''REPORTED'' COMMENT ''报案状态(REPORTED/CLOSED)'',
     case_count        INT           NOT NULL DEFAULT 0 COMMENT ''已建赔案数(分案递增)'',
     tenant_id         BIGINT        NOT NULL COMMENT ''租户'',
     created_by        BIGINT        DEFAULT NULL COMMENT ''创建人ID'',
     created_at        DATETIME(3)   NOT NULL COMMENT ''创建时间'',
     updated_by        BIGINT        DEFAULT NULL COMMENT ''修改人ID'',
     updated_at        DATETIME(3)   NOT NULL COMMENT ''更新时间'',
     version           BIGINT        NOT NULL DEFAULT 0 COMMENT ''乐观锁版本'',
     deleted           TINYINT       NOT NULL DEFAULT 0 COMMENT ''软删标记'',
     PRIMARY KEY (id),
     UNIQUE KEY uk_report_no (report_no),
     UNIQUE KEY uk_tenant_report_idem (tenant_id, idempotency_key),
     KEY idx_tenant_report_status (tenant_id, status, created_at)
   ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = ''报案主表(FNOL, 1:N的1)''',
  'DO 0');
PREPARE create_report_stmt FROM @create_report;
EXECUTE create_report_stmt;
DEALLOCATE PREPARE create_report_stmt;

-- ---------- 阶段 2：t_claim_case 加列（幂等） ----------
SET @has_report_id = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND column_name = 'report_id'
);
SET @add_report_id = IF(@has_report_id = 0,
  'ALTER TABLE t_claim_case ADD COLUMN report_id BIGINT DEFAULT NULL COMMENT ''归属报案ID(1:N的多指向一)'' AFTER claim_no',
  'DO 0');
PREPARE add_report_id_stmt FROM @add_report_id;
EXECUTE add_report_id_stmt;
DEALLOCATE PREPARE add_report_id_stmt;

SET @has_report_no = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND column_name = 'report_no'
);
SET @add_report_no = IF(@has_report_no = 0,
  'ALTER TABLE t_claim_case ADD COLUMN report_no VARCHAR(64) DEFAULT NULL COMMENT ''归属报案号(RP前缀,冗余展示)'' AFTER report_id',
  'DO 0');
PREPARE add_report_no_stmt FROM @add_report_no;
EXECUTE add_report_no_stmt;
DEALLOCATE PREPARE add_report_no_stmt;

-- ---------- 阶段 3：建索引（幂等） ----------
SET @has_idx_report = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case' AND index_name = 'idx_tenant_report'
);
SET @add_idx_report = IF(@has_idx_report = 0,
  'ALTER TABLE t_claim_case ADD KEY idx_tenant_report (tenant_id, report_id)',
  'DO 0');
PREPARE add_idx_report_stmt FROM @add_idx_report;
EXECUTE add_idx_report_stmt;
DEALLOCATE PREPARE add_idx_report_stmt;

-- ---------- 阶段 4：终态断言（fail-closed） ----------
-- 4.1 报案表存在
SET @assert_table = IF(@has_report_table > 0 OR (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report') > 0,
  'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: t_claim_report missing''');
PREPARE assert_table_stmt FROM @assert_table;
EXECUTE assert_table_stmt;
DEALLOCATE PREPARE assert_table_stmt;

-- 4.2 report_no 全局唯一键存在且列序严格为 report_no
SET @report_no_cols = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report'
    AND index_name = 'uk_report_no' AND non_unique = 0
);
SET @assert_uk = IF(@report_no_cols = 'report_no', 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: uk_report_no missing or wrong columns''');
PREPARE assert_uk_stmt FROM @assert_uk;
EXECUTE assert_uk_stmt;
DEALLOCATE PREPARE assert_uk_stmt;

-- 4.3 t_claim_case 两列齐全
SET @case_cols = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND column_name IN ('report_id', 'report_no')
);
SET @assert_cols = IF(@case_cols = 2, 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0139 FAIL: t_claim_case report columns missing''');
PREPARE assert_cols_stmt FROM @assert_cols;
EXECUTE assert_cols_stmt;
DEALLOCATE PREPARE assert_cols_stmt;

SELECT '0139 OK: 报案 1:N 表结构与归属列已就位' AS result;
