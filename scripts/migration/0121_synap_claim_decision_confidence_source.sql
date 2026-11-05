-- ============================================================
-- 0121_synap_claim_decision_confidence_source.sql
--
-- 一句话：给 claim_decision_execution 增加 confidence_source 列——
--         让「这个置信度是谁给的」成为库内可查的事实，而不是消费方的猜测。
--
-- 背景（为什么必须加）
-- ----------------------------------------------------------------
-- ai_confidence 一列在不同链路下装的是不同性质的数：
--   · LLM 节点成功 → 模型自报置信度（概率判断）；
--   · LLM 降级     → 纯规则路径的 ruleConfidence（确定性计算）；
--   · 桩环境       → 确定性桩写死的常数。
-- 消费方（阈值分诊 AI_CONFIDENCE_LOW、评测导出、运维看板）只能看到数值，
-- 无法区分来源 ⇒ 阈值语义失真：桩永远同一常数时，「阈值 0.9 以上放行」
-- 在灰度环境看起来永远通过，调好的阈值被桩数据污染。
-- 应用侧已随本迁移落码：DecisionExecution.complete(result, tier, confidence,
-- fallback, confidenceSource) 在落结论时点推导并写入（DecisionGraphExecutor
-- 三处置信度覆写分支逐一同步）。
--
-- 形态（为什么是 varchar 受控码）
-- ----------------------------------------------------------------
-- 与同表 triage_reason / consumption_reason 同款：受控短码、可读，
-- 应用侧枚举 AiConfidenceSource 守值域。列 DEFAULT NULL：
--   · NULL = 本列加入前的历史行（当时不存在该维度）或未落结论的执行；
--   · 代码写入时 confidence 为 null 也落 UNKNOWN（有结论但没产生置信度），
--     与 NULL 明确区分（见 AiConfidenceSource 类注释）。
--
-- 存量数据（为什么不做回填）
-- ----------------------------------------------------------------
-- 历史行的来源已不可考证（当时的链路分支没有留下任何事实），任何回填值
-- 都是编造 ⇒ 保持 NULL，消费方按「历史行无溯源」处理。这不是偷懒：
-- 编造一个 MODEL 出去比留 NULL 危险得多。
--
-- 终态断言（fail-closed）
-- ----------------------------------------------------------------
-- 不靠「SQL 没报错」判成功：用 information_schema 强断言列存在。
-- 不做值域 CHECK：历史行 NULL 合法，新写入由应用枚举守值域。
-- ============================================================

ALTER TABLE claim_decision_execution
  ADD COLUMN confidence_source VARCHAR(16) DEFAULT NULL
  COMMENT '置信度来源受控码(MODEL模型自报/STUB桩常数/FALLBACK规则路径/UNKNOWN未产生); NULL=本列加入前的历史行或未落结论'
  AFTER ai_confidence;

-- 终态断言：列必须存在（脚本「没报错」不等于「做到」）。IF 只能在过程上下文
-- 使用，与 0118 同款：临时过程 + CALL + DROP，不留过程体。
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_verify_0121$$
CREATE PROCEDURE sp_verify_0121()
BEGIN
    DECLARE v_missing INT DEFAULT 0;

    SELECT COUNT(*) INTO v_missing
      FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'claim_decision_execution'
       AND column_name = 'confidence_source';
    IF v_missing <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '0121 terminal assert failed: confidence_source column missing';
    END IF;
END$$
DELIMITER ;

CALL sp_verify_0121();
DROP PROCEDURE IF EXISTS sp_verify_0121;
