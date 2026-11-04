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
       triage_reason,
       consumption_reason,
       consumed_at,
       tenant_id,
       created_at,
       updated_at,
       version
FROM claim_decision_execution
WHERE status IN ('SUCCESS', 'FALLBACK') AND
  finished_at IS NOT NULL AND
  finished_at >= :from AND
  finished_at < :to AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY finished_at DESC, id DESC
LIMIT 500
