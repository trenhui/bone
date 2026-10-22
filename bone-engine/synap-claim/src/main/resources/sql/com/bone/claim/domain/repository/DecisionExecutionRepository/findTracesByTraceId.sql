-- 决策轨迹回放（按 trace_id 取全链节点序列）
SELECT id,
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
WHERE trace_id = :traceId
  /*bone:tenant*/
ORDER BY node_seq
