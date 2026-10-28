SELECT
       id,
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
WHERE claim_id = :claimId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY started_at DESC
LIMIT 1
