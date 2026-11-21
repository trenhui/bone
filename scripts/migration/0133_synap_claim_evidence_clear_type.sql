-- ============================================================
-- 0133_synap_claim_evidence_clear_type.sql
--
-- 一句话：给 t_claim_evidence 增加 clear_type 列——让「这张影像能否入理算」
--         成为库内可查、写入期可校验的事实，而不是看图人脑子里的判断。
--
-- 背景（为什么必须加）
-- ----------------------------------------------------------------
-- 原型 claim-image-detail.html 的绿/红角标（清晰可入理算 / 不清晰·不完整需补传）
-- 在落地实现里只有分类树、关联发票、旋转/全屏快捷键，缺清晰度三态这一维度。
-- 没有这一列：① 抽取字段无法感知原件质量，"可入理算"无判据；
-- ② 锁定证据集时无法 fail-closed 阻断"看图不清却已锁定出金"。
--
-- 形态（为什么是 varchar 受控码 + NOT NULL DEFAULT 'CLEAR'）
-- ----------------------------------------------------------------
-- 三态 CLEAR / UNCLEAR / INCOMPLETE 与原型一一对应，应用侧枚举 ImageClarity 守值域。
-- DEFAULT 'CLEAR' 对存量最兼容；但"未标注=可入理算"有风险，故锁定端点
-- EvidenceApplicationService.lockAll 在锁定前显式校验三态并对非 CLEAR 抛
-- SC_CLAIM_EVIDENCE_UNCLEAR_BLOCKS_LOCK（写入期兜底，见应用服务注释）。
--
-- 存量数据（为什么不做回填）
-- ----------------------------------------------------------------
-- 历史行没有"当时看清晰度如何"的事实，回填 CLEAR 是编造；默认值即历史口径，
-- 靠锁定端点的显式校验兜底，而不是给每一行伪造一个过去的判断。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- 用 information_schema 强断言列存在，SQL「没报错」不等于「做到」。
-- ============================================================

ALTER TABLE t_claim_evidence
  ADD COLUMN clear_type VARCHAR(16) NOT NULL DEFAULT 'CLEAR'
  COMMENT '清晰度受控码(CLEAR清晰可入理算/UNCLEAR不清晰/INCOMPLETE不完整); 非CLEAR锁定证据集时被阻断'
  AFTER verify_valid;

DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0133$$
CREATE PROCEDURE sp_verify_0133()
BEGIN
    DECLARE v_missing INT DEFAULT 0;
    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 't_claim_evidence'
       AND column_name = 'clear_type';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0133 terminal assert failed: clear_type column missing';
    END IF;
END$$
DELIMITER ;
CALL sp_verify_0133();
DROP PROCEDURE IF EXISTS sp_verify_0133;
