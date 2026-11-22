-- ============================================================
-- 0138_synap_claim_claim_no_global_unique.sql
--
-- 一句话：把报案号 claim_no 的唯一约束从「租户内唯一」升级为「全局唯一」，
--        与新生成器 ClaimNoGenerator（雪花 ID 经 Feistel 双射混淆）的全局唯一承诺对齐。
--
-- 背景（为什么必须做）
-- ----------------------------------------------------------------
-- 新报案号生成器对全局唯一的雪花 ID 做 6 轮 Feistel 置换，输出天然全局唯一；
-- ClaimNoGenerator 的类注释与契约 §9.3.1 明确承诺「全局唯一、零碰撞」。
-- 但物理约束仍是 V1 的 uk_tenant_claim_no (tenant_id, claim_no)——它只保证租户内唯一，
-- 不同租户理论上可存在相同 claim_no。这是「代码承诺全局唯一、数据库只兜租户唯一」的
-- 语义落差：一旦某环节出错（多实例共用 workerId、外部导入重号、有人绕过生成器手填），
-- 旧索引不会拦截，重号会静默落库，正好击穿报案号作为对账/检索键的根基。
--
-- 系统尚未上线，无存量数据，升级为零风险。但仍按项目铁律做脏数据探测：
-- 若真有跨租户重复 claim_no，不自动删（删哪条是业务决定），SIGNAL 中止并打印清单。
--
-- 口径
-- ----------------------------------------------------------------
--   · 删除旧 uk_tenant_claim_no (tenant_id, claim_no)；
--   · 新增全局 uk_claim_no (claim_no)——不带 tenant_id，跨租户唯一；
--   · 幂等：information_schema.statistics 判定两个索引的存在性，缺才建、有才删；
--   · 终态断言：全局唯一键存在且列序严格为 claim_no、旧索引已消失、无重复 claim_no。
--
-- 关联（必须同提交）
-- ----------------------------------------------------------------
--   · ClaimNoGenerator.java：Feistel 双射生成器（唯一性真源）
--   · ClaimCase.java：draft() 委托生成器
--   · bone-init.sql：全量基线同批改索引与列注释
--   · db/migration/V1__synap_claim_init.sql：模块参考态同批改（保持同源）
--   · doc/ai-product/synapClaim/03-contract/工程详细设计-落地版.md §9.3/§9.3.1/§9.5
--
-- 执行：
--   mysql --default-character-set=utf8mb4 -h <host> -u <user> -p bone < 0138_synap_claim_claim_no_global_unique.sql
-- ============================================================

-- ---------- 阶段 1：脏数据探测（跨租户重复 claim_no 必须先停手，不自动删） ----------
SET @dup_count = (
  SELECT COUNT(*) FROM (
    SELECT claim_no FROM t_claim_case GROUP BY claim_no HAVING COUNT(*) > 1
  ) AS d
);

-- MESSAGE_TEXT ≤128 字符（MySQL ERROR 1648 硬限制），故只放简短指引。
SET @msg = CONCAT('0138 ABORT: 存在 ', @dup_count, ' 个重复 claim_no，先人工处置再重跑本迁移');
SELECT @dup_count AS duplicate_claim_no_groups;

-- 用预处理语句实现条件 SIGNAL（MySQL 不能在 IF 外直接 SIGNAL 变量拼接的文案）
SET @probe_sql = IF(
  @dup_count > 0,
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0138 ABORT: duplicate claim_no exists; resolve manually then rerun''',
  'DO 0'
);
PREPARE probe_stmt FROM @probe_sql;
EXECUTE probe_stmt;
DEALLOCATE PREPARE probe_stmt;

-- 打印重复清单供 DBA 处置（无重复时为空结果集）
SELECT claim_no, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids, GROUP_CONCAT(DISTINCT tenant_id) AS tenants
FROM t_claim_case
GROUP BY claim_no
HAVING COUNT(*) > 1;

-- ---------- 阶段 2：索引升级（幂等，information_schema 判定 + 动态 DDL） ----------
-- 删旧租户级唯一键（存在才删）
SET @has_old = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'uk_tenant_claim_no'
);
SET @drop_old = IF(@has_old > 0,
  'ALTER TABLE t_claim_case DROP INDEX uk_tenant_claim_no',
  'DO 0');
PREPARE drop_old_stmt FROM @drop_old;
EXECUTE drop_old_stmt;
DEALLOCATE PREPARE drop_old_stmt;

-- 建全局唯一键（不存在才建）
SET @has_new = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'uk_claim_no'
);
SET @add_new = IF(@has_new = 0,
  'ALTER TABLE t_claim_case ADD UNIQUE KEY uk_claim_no (claim_no)',
  'DO 0');
PREPARE add_new_stmt FROM @add_new;
EXECUTE add_new_stmt;
DEALLOCATE PREPARE add_new_stmt;

-- ---------- 阶段 3：终态断言（fail-closed：不满足则中止） ----------
-- 3.1 新全局唯一键存在，且列序严格为 claim_no（第 1 列）且仅 1 列
SET @new_cols = (
  SELECT GROUP_CONCAT(column_name ORDER BY seq_in_index)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'uk_claim_no' AND non_unique = 0
);
SET @assert_new = IF(@new_cols = 'claim_no', 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0138 FAIL: uk_claim_no missing or wrong columns''');
PREPARE assert_new_stmt FROM @assert_new;
EXECUTE assert_new_stmt;
DEALLOCATE PREPARE assert_new_stmt;

-- 3.2 旧租户级唯一键已消失
SET @old_left = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 't_claim_case'
    AND index_name = 'uk_tenant_claim_no'
);
SET @assert_old = IF(@old_left = 0, 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0138 FAIL: legacy uk_tenant_claim_no still exists''');
PREPARE assert_old_stmt FROM @assert_old;
EXECUTE assert_old_stmt;
DEALLOCATE PREPARE assert_old_stmt;

-- 3.3 无重复 claim_no（加约束后理论上不可能，双保险）
SET @dup_after = (
  SELECT COUNT(*) FROM (
    SELECT claim_no FROM t_claim_case GROUP BY claim_no HAVING COUNT(*) > 1
  ) AS d
);
SET @assert_dup = IF(@dup_after = 0, 'DO 0',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''0138 FAIL: duplicate claim_no after migration''');
PREPARE assert_dup_stmt FROM @assert_dup;
EXECUTE assert_dup_stmt;
DEALLOCATE PREPARE assert_dup_stmt;

SELECT '0138 OK: claim_no 全局唯一索引已就位' AS result;
