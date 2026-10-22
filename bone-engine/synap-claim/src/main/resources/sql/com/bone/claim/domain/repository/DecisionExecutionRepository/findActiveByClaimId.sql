-- 案件当前活跃决策执行（每案至多一条，uk_claim_active 兜底）
SELECT id,
       trace_id,
       claim_id,
       graph_version,
       rule_version,
       model_version,
       prompt_version,
       catalog_version,
       status,
       active_flag,
       decision_result,
       decision_tier,
       ai_confidence,
       started_at,
       finished_at,
       duration_ms,
       tenant_id,
       created_at,
       updated_at,
       version
FROM claim_decision_execution
WHERE claim_id = :claimId
  AND active_flag = 1
  /*bone:tenant*/
LIMIT 1
