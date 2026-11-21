-- ============================================================
-- 0134_synap_claim_coverage_override_origin.sql
--
-- 一句话：给 t_claim_coverage_override 增加 origin / last_sync_version 两列——
--         让「通用继承 / 专属覆盖 / 专属·改」三态成为库内事实，而不是前端猜测。
--
-- 背景（为什么必须加）
-- ----------------------------------------------------------------
-- 原型 job-config.html / form-config.html 定义三态：
--   · gen 通用·继承标准（标准升级会覆盖）
--   · exc 专属·覆盖标准（本主体新增/覆盖）
--   · mod 专属·改·曾调整（改过标准值，标准升级不再覆盖）
-- 现实现只有 DISABLED/ACTIVE 两态，靠 standardValue != overrideValue 快照对比
-- 推断，表达不出三态；且"同步标准字段默认不启用、已有专属不受覆盖"缺写操作留痕。
--
-- 形态（为什么是两列）
-- ----------------------------------------------------------------
--   origin：INHERIT / EXCLUSIVE / MODIFIED 受控码，DEFAULT 'EXCLUSIVE'（存量覆盖行
--           本就是专属写入，归 EXCLUSIVE 最贴切；若 standard!=override 视为 MODIFIED
--           由应用侧升级时判定，不做 SQL 回填编造）。
--   last_sync_version：VARCHAR(32) DEFAULT NULL，记录最近一次"同步标准"的版本号，
--           为"可回退/可追溯"提供锚点；NULL=尚未同步过或历史行。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- information_schema 强断言两列都存在。
-- ============================================================

ALTER TABLE t_claim_coverage_override
  ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'EXCLUSIVE'
  COMMENT '覆盖来源三态(INHERIT通用继承/EXCLUSIVE专属覆盖/MODIFIED专属·改，标准升级不再覆盖)'
  AFTER status,
  ADD COLUMN last_sync_version VARCHAR(32) DEFAULT NULL
  COMMENT '最近一次同步标准字段的版本号(可回退/追溯锚点); NULL=未同步过或历史行'
  AFTER origin;

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0134$$
CREATE PROCEDURE sp_verify_0134()
BEGIN
    DECLARE v_missing INT DEFAULT 0;
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_coverage_override'
       AND column_name IN ('origin', 'last_sync_version');
    IF v_missing <> 2 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0134 terminal assert failed: origin/last_sync_version columns missing';
    END IF;
END$$
DELIMITER ;
CALL sp_verify_0134();
DROP PROCEDURE IF EXISTS sp_verify_0134;
