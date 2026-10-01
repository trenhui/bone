-- ============================================================
-- 0014_masterdata_field_range_and_record_business_key.sql
-- 目的：
--   ① 字段定义补齐数值值域（min_value / max_value），让「建模时声明的值域」在写入时真正生效
--      （上一轮只支持参考数据枚举型值域，单价 >0、折扣率 ≤1 这类区间约束无法表达）。
--   ② 存量记录补登业务主键（record_code / display_name）。
--      真实断链：创建接口此前根本没有把 recordCode/displayName 落库，mdm_record.record_code 全为 NULL，
--      下游只能靠 current_data 里的 JSON 字段定位记录，无法按业务编码去重 / 检索 / 订阅分发。
-- 依据：ADR-生产数据库增量迁移策略（expand/contract，先加列后回填，不下线旧读路径）
-- 前置：0001_masterdata_mdm_convergence.sql
-- 回滚：见文件末尾
-- 幂等：可重复执行（ADD COLUMN 前先判 information_schema；回填只处理 record_code IS NULL 的行）
-- ============================================================

-- ---------- 阶段 1：Expand —— 字段表加值域列 ----------
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mdm_field' AND COLUMN_NAME = 'min_value') > 0,
  'SELECT ''skip min_value''',
  'ALTER TABLE mdm_field ADD COLUMN min_value DECIMAL(30,6) NULL COMMENT ''数值字段取值下限（NUMBER 类型生效）'' AFTER reference_set_code'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mdm_field' AND COLUMN_NAME = 'max_value') > 0,
  'SELECT ''skip max_value''',
  'ALTER TABLE mdm_field ADD COLUMN max_value DECIMAL(30,6) NULL COMMENT ''数值字段取值上限（NUMBER 类型生效）'' AFTER min_value'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------- 阶段 2：Expand —— 记录业务主键唯一索引 ----------
-- 业务语义：同一租户 + 同一实体内 record_code 唯一。
-- 核查结论（2026-10-01）：本库已存在 uk_mdm_record_code (tenant_id, mdm_entity_id, record_code)，
-- 即「记录业务主键唯一」的 DB 约束早已就绪——真正断的是应用层根本没往这列写值。
-- 因此本阶段只做存在性断言，不重复建索引；新环境缺失时才补建。
SET @ddl := IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'mdm_record'
      AND INDEX_NAME IN ('uk_mdm_record_code', 'uk_mdm_record_biz_key')) > 0,
  'SELECT ''skip record_code unique key (already exists)''',
  'ALTER TABLE mdm_record ADD UNIQUE KEY uk_mdm_record_code (tenant_id, mdm_entity_id, record_code)'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 唯一索引允许多个 NULL，故存量 NULL 行不会互相冲突，可安全保留待补登。

-- ---------- 阶段 3：Backfill —— 存量记录补登业务主键 ----------
-- 约定：从 current_data 中按「业务编码键优先序」抽取 code 作为 record_code，
--       按「名称键优先序」抽取 name 作为 display_name。仅处理 record_code IS NULL 的行。
--
-- 去重守卫（踩坑记录，两次才跑通）：
--   ① 首跑直接 UPDATE 撞 uk_mdm_record_code
--      Duplicate entry '0-758871380975419392-CUST-LT-001'——存量库里同一实体下本就有同码的多条记录。
--   ② 二跑加 NOT EXISTS 反连接仍然撞同一条错：MySQL 在同一条 UPDATE 内看不到自己刚写入的行，
--      同批次两行算出同一个 code 时互相都"看不见对方"，双双通过反连接判定。
--   正确做法：先按 (tenant, entity, code) 分组，只让 MIN(id) 那一行胜出（同码重复行留待人工治理），
--      再与"已有该 code 的行"做 LEFT JOIN 反连接，双保险。
DROP TEMPORARY TABLE IF EXISTS tmp_md_biz_key;
CREATE TEMPORARY TABLE tmp_md_biz_key AS
SELECT MIN(id) AS id, tenant_id, mdm_entity_id, code
FROM (
  SELECT id, tenant_id, mdm_entity_id,
         COALESCE(
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.code')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.product_code')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.customer_code')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.supplier_code')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.level_code')), ''),
           NULLIF(JSON_UNQUOTE(JSON_EXTRACT(current_data, '$.material_code')), '')
         ) AS code
  FROM mdm_record
  WHERE record_code IS NULL AND deleted = 0
) x
WHERE code IS NOT NULL
GROUP BY tenant_id, mdm_entity_id, code;

UPDATE mdm_record r
  JOIN tmp_md_biz_key t ON t.id = r.id
  LEFT JOIN mdm_record o
    ON o.tenant_id = t.tenant_id AND o.mdm_entity_id = t.mdm_entity_id AND o.record_code = t.code
SET r.record_code = t.code
WHERE o.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_md_biz_key;

UPDATE mdm_record r
SET r.display_name = COALESCE(
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.product_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.customer_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.supplier_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.level_name')), '')
    )
WHERE r.display_name IS NULL
  AND COALESCE(
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.product_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.customer_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.supplier_name')), ''),
      NULLIF(JSON_UNQUOTE(JSON_EXTRACT(r.current_data, '$.level_name')), '')
    ) IS NOT NULL;

-- ---------- 阶段 4：验证（人工确认，非 DML） ----------
-- SELECT mdm_entity_id, COUNT(*) AS total, SUM(record_code IS NULL) AS null_code
--   FROM mdm_record WHERE deleted = 0 GROUP BY mdm_entity_id;

-- ---------- 回滚（Contract） ----------
-- ALTER TABLE mdm_record DROP INDEX uk_mdm_record_biz_key;
-- ALTER TABLE mdm_field DROP COLUMN max_value;
-- ALTER TABLE mdm_field DROP COLUMN min_value;
-- （record_code / display_name 的回填不回滚：它们本就是应补登的正确值）
