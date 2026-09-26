-- ============================================================
-- 0008_generator_entity_name_pascal.sql
-- gen_table_metadata.custom_entity_name 存量修正：原始表名 → 大驼峰
--
-- 背景：
--   GenTableMetadata.toEntityName 已实现「剥离 t_ 前缀 + 下划线转大驼峰」，create() 会调用；
--   但 updateFrom() 故意不更新 custom_entity_name —— 该字段语义是"用户可自定义实体名"，
--   每次同步都覆盖会丢掉用户改名。因此 toEntityName 落地之前同步的存量行仍保留原始表名，
--   生成产物出现 `public class bone_application`、包名含下划线（可编译但不符合命名规范）。
--
-- 策略：仅修正 custom_entity_name = original_table_name 的行（即从未被用户改名过的）；
--       已被用户自定义的行保持原值不动。
-- 依据：bone-engine/studio-generator/src/main/java/com/bone/studio/generator/
--       domain/model/data/GenTableMetadata.java #toEntityName
-- 前置：无；本脚本幂等，可重复执行。
-- 执行前：必须备份（见「阶段 0」）。
-- 权限：CREATE FUNCTION 需 SUPER 或 log_bin_trust_function_creators=ON；
--       不满足时不要强行执行，改用应用层一次性任务（逻辑同 bone_pascal）。
-- ============================================================

-- ---------- 阶段 0：备份 ----------
CREATE TABLE IF NOT EXISTS bak_gen_table_metadata_0008 AS SELECT * FROM gen_table_metadata;

-- ---------- 阶段 1：转换函数（与 Java toEntityName 同逻辑） ----------
DROP FUNCTION IF EXISTS bone_pascal;
DELIMITER //
CREATE FUNCTION bone_pascal(t VARCHAR(128)) RETURNS VARCHAR(128) DETERMINISTIC
BEGIN
    DECLARE s      VARCHAR(128) DEFAULT t;
    DECLARE o      VARCHAR(128) DEFAULT '';
    DECLARE i      INT          DEFAULT 1;
    DECLARE c      CHAR(1);
    DECLARE upnext BOOLEAN      DEFAULT TRUE;

    IF s IS NULL THEN
        RETURN NULL;
    END IF;

    -- 剥离技术前缀 t_ / T_
    IF LEFT(s, 2) IN ('t_', 'T_') THEN
        SET s = SUBSTRING(s, 3);
    END IF;

    WHILE i <= CHAR_LENGTH(s) DO
        SET c = SUBSTRING(s, i, 1);
        IF c = '_' THEN
            SET upnext = TRUE;
        ELSE
            SET o = CONCAT(o, IF(upnext, UPPER(c), c));
            SET upnext = FALSE;
        END IF;
        SET i = i + 1;
    END WHILE;

    RETURN IF(o = '', t, o);
END//
DELIMITER ;

-- ---------- 阶段 2：修正（仅从未被用户改名的行） ----------
UPDATE gen_table_metadata
SET custom_entity_name = bone_pascal(original_table_name)
WHERE deleted = 0
  AND custom_entity_name IS NOT NULL
  AND custom_entity_name = original_table_name;

-- ---------- 阶段 3：校验 ----------
-- 预期返回 0 行；若仍有返回，说明这些行的实体名曾被用户自定义（属预期，无需处理）
SELECT id,
       data_source_id,
       original_table_name,
       custom_entity_name
FROM gen_table_metadata
WHERE deleted = 0
  AND custom_entity_name IS NOT NULL
  AND custom_entity_name = original_table_name;

-- ---------- 阶段 4：清理临时函数 ----------
DROP FUNCTION IF EXISTS bone_pascal;
