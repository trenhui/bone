SELECT
       id,
       trace_id,
       claim_id,
       execution_id,
       node_seq,
       node_type,
       node_name,
       input_summary,
       output,
       action_result,
       duration_ms,
       created_at,
       tenant_id
FROM claim_decision_trace
WHERE node_name = :nodeName AND
  created_at >= :from AND
  created_at < :to AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY created_at DESC, id DESC
LIMIT 2000
