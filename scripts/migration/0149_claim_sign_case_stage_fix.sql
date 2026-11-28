-- ============================================================
-- 0149_claim_sign_case_stage_fix.sql
--
-- 一句话：把 TPA 回填的「AUTO_PRE_EXAMINING + REVIEW」纠正为合法阶段 PRE_EXAM。
--
-- 口径
-- ----------------------------------------------------------------
--   · stage 与 status 正交但必须自洽：AUTO_PRE_EXAMINING 是自动化初审态，
--     对应 ClaimStage.PRE_EXAM；REVIEW 根本不是枚举值（合法值为 REVIEWING）；
--   · 案件主表与案件轨迹同批修正，避免 360 页阶段标签与轨迹不一致；
--   · 幂等 UPDATE + 存储过程 SIGNAL(1644) 终态断言，可重复执行；
--   · 回填脚本同步改为 PRE_EXAM，防止下次重跑再次写入非法值。
-- ============================================================

USE bone;

UPDATE t_claim_case
SET stage = 'PRE_EXAM'
WHERE stage = 'REVIEW' AND status = 'AUTO_PRE_EXAMINING';

UPDATE t_claim_case_track
SET stage = 'PRE_EXAM'
WHERE stage = 'REVIEW' AND to_status = 'AUTO_PRE_EXAMINING';

SELECT COUNT(*) INTO @bad_stage_count_0149
FROM t_claim_case
WHERE stage = 'REVIEW' AND status = 'AUTO_PRE_EXAMINING';

SELECT COUNT(*) INTO @bad_track_count_0149
FROM t_claim_case_track
WHERE stage = 'REVIEW' AND to_status = 'AUTO_PRE_EXAMINING';

DROP PROCEDURE IF EXISTS sp_verify_0149;
DELIMITER $$
CREATE PROCEDURE sp_verify_0149()
BEGIN
  IF @bad_stage_count_0149 > 0 OR @bad_track_count_0149 > 0 THEN
    SIGNAL SQLSTATE '45000' SET MYSQL_ERRNO = 1644,
      MESSAGE_TEXT = '0149 FAIL: illegal REVIEW stage remains on auto pre-exam cases';
  END IF;
END$$
DELIMITER ;
CALL sp_verify_0149();
DROP PROCEDURE IF EXISTS sp_verify_0149;
