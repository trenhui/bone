-- 0140 修复 t_claim_report.plan_id 列可为 NULL（报案登记时计划尚未确定）
-- 背景：0139 建表把 plan_id 定为 NOT NULL 且无默认值，但 ClaimReport 聚合根没有 planId 字段
--       （FNOL 报案阶段只有保单号 policy_no，计划要等分案/理算才确定），登记 INSERT 不带该列
--       ⇒ 干净库与活库均报「Field 'plan_id' doesn't have a default value」(SQL 1364)，
--       报案登记 500、1:N 链路不可通。此为 0139 的代码/DDL 语义落差，0139 已发不可回改，按纪律写新迁移修正。
-- 幂等：information_schema 判定列是否可空，已是 NULL 则 DO 0；终态断言 plan_id IS_NULLABLE=YES。

SET @col_nullable = (
  SELECT IS_NULLABLE FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report' AND column_name = 'plan_id'
);
SET @has_col = @col_nullable IS NOT NULL;
SET @modify = IF(@col_nullable = 'NO',
  'ALTER TABLE t_claim_report MODIFY COLUMN plan_id BIGINT NULL COMMENT ''计划ID(报案登记时未确定; 分案/理算时回填)''',
  'DO 0');
PREPARE modify_stmt FROM @modify;
EXECUTE modify_stmt;
DEALLOCATE PREPARE modify_stmt;

-- 终态断言：列存在且可空（缺列/仍 NOT NULL 即 SIGNAL，fail-closed）
SET @final_nullable = (
  SELECT IS_NULLABLE FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 't_claim_report' AND column_name = 'plan_id'
);
SET @assert = IF(@final_nullable = 'YES', 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0140 FAIL: t_claim_report.plan_id not nullable''');
PREPARE assert_stmt FROM @assert;
EXECUTE assert_stmt;
DEALLOCATE PREPARE assert_stmt;

SELECT '0140 OK: t_claim_report.plan_id 可空，报案登记可写入' AS result;
