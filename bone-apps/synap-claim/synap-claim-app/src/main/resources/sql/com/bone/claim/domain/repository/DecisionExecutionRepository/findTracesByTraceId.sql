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
WHERE trace_id = :traceId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY node_seq
