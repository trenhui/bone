-- 0110_fix_meta_entity_ui_schema_audit_columns.sql
--
-- 目的：把 meta_entity_ui_schema 的两个审计列（created_by / updated_by）**逐列独立**补齐。
--
-- 背景（2026-11-03 实测，由 check-ddl-duplicate-column.py 抓出）
-- --------------------------------------------------------------
-- 0046 的幂等补列写成了「一次 ADD 两列、只探测一列」：
--     SET @cb_exists = (... COLUMN_NAME = 'created_by');
--     SET @cb_ddl = IF(@cb_exists = 0,
--         'ALTER TABLE meta_entity_ui_schema ADD COLUMN created_by ...,
--          ADD COLUMN updated_by ...', 'SELECT 1');
-- 后果有两类，且都**静默**：
--   ① 存量库「有 created_by、缺 updated_by」⇒ @cb_exists=1 ⇒ 整段跳过 ⇒ updated_by
--      **永远补不上**。SDK 写路径按实体 @Column 生成 INSERT，缺列即 BadSqlGrammar
--      （表存在但接口 500，排查方向常被带偏到"服务没起"）；
--   ② 存量库「缺 created_by、有 updated_by」⇒ @cb_exists=0 ⇒ 执行 ADD 两列 ⇒
--      updated_by 重复 ⇒ ERROR 1060 Duplicate column name，脚本**中止**（后面所有
--      迁移都跑不到）。这是"探测不全 ⇒ 重复执行"的典型形态：探测与执行的对象集不一致。
--
-- 为什么新增脚本而不回改 0046
-- --------------------------
-- scripts/migrate.py 按 sha256 记 journal（已应用脚本内容变更 = drift 报错），
-- 回改 0046 会让**所有已应用环境**报 drift，需逐环境走"承认历史"流程，风险扩散。
-- 业界惯例同样是「已发迁移不回改，用新迁移修」。本脚本只补列、不碰任何既有结构。
--
-- 幂等：逐列独立探测（information_schema）+ 动态 DDL，缺哪列补哪列；
--       两种存量形态（有 created_by 缺 updated_by / 两列都缺）都能安全补齐，
--       连跑不报错、不重复添加。
-- 终态断言：两列必须都在，否则 SIGNAL 中止（fail-closed，不留半补状态）。

-- ---------- ① created_by ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE meta_entity_ui_schema ADD COLUMN created_by BIGINT NULL COMMENT ''创建人（AbstractEntity 审计字段）'' AFTER updated_at',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_entity_ui_schema'
    AND COLUMN_NAME = 'created_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- ② updated_by（独立探测，不与 created_by 共用判定） ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE meta_entity_ui_schema ADD COLUMN updated_by BIGINT NULL COMMENT ''更新人（AbstractEntity 审计字段）'' AFTER created_by',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'meta_entity_ui_schema'
    AND COLUMN_NAME = 'updated_by'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 终态断言 ----------
DROP PROCEDURE IF EXISTS `tmp_assert_0110`;
DELIMITER $$
CREATE PROCEDURE `tmp_assert_0110`()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM (
        SELECT 'created_by' AS c UNION ALL
        SELECT 'updated_by'
      ) AS expected
     WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS col
         WHERE col.table_schema = DATABASE()
           AND col.table_name = 'meta_entity_ui_schema'
           AND col.column_name = expected.c
    );

    IF v_missing <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0110 校验失败：meta_entity_ui_schema 审计列未补齐（SDK 写路径缺列即 BadSqlGrammar）';
    END IF;
END$$
DELIMITER ;
CALL `tmp_assert_0110`();
DROP PROCEDURE IF EXISTS `tmp_assert_0110`;
