-- =====================================================================
-- 0091 synap-claim 决策分诊原因码落库（triage_reason）
-- ---------------------------------------------------------------------
--   claim_decision_execution + triage_reason VARCHAR(32)
--
-- 为什么必须落库：决策结论（decision_result）只说"落点"（AUTO_APPROVE /
-- MANUAL_REQUIRED / ...），分诊原因码（DecisionTriage.Outcome.reason）才说
-- "为什么"（RISK_HIT / AMOUNT_OVER_LIMIT / AI_CONFIDENCE_LOW / ...）。
-- 此前原因码只存在于 POST /decision/execute 的响应里，读侧一律看不见：
--   ① 快捷赔付风险闸只能拦"建议拒赔"，拦不住"风控命中"（内鬼/被盗账号对
--      RISK_HIT 案件仍可一键放款）；
--   ② 核赔工作台/处置归因无法从库内证明"该案曾被风控拦截"，只能翻决策响应
--      日志。
-- 与 consumption_reason（0079 追加）同一模式：取决于执行当时的事实，事后用
-- 当前配置重算推不出来，必须落库而非推导。
--
-- 历史数据不回填、不猜测：NULL = 异常中止未经分诊，或历史行无原因可记。
-- 快捷赔付闸对 NULL 不误拦（闸只对"已知判否"生效，不做猜测式拒绝）。
-- =====================================================================

-- ---------- ① claim_decision_execution.triage_reason ----------
SET @ddl := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE claim_decision_execution ADD COLUMN triage_reason VARCHAR(32) DEFAULT NULL COMMENT ''分诊受控原因码(RISK_HIT/AMOUNT_OVER_LIMIT/AI_CONFIDENCE_LOW/OCR_CONFIDENCE_LOW/FRAUD_GATE_NOT_PASSED/EVIDENCE_MISSING等); NULL=异常中止未经分诊或历史行无原因'' AFTER duration_ms',
    'DO 0'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'claim_decision_execution' AND COLUMN_NAME = 'triage_reason'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
