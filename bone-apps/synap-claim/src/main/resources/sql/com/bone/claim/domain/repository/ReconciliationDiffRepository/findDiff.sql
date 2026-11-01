SELECT
       id,
       report_id,
       claim_no,
       settlement_no,
       diff_type,
       local_amount,
       remote_amount,
       local_stage,
       remote_status,
       resolution,
       resolved_by,
       tenant_id,
       created_at,
       updated_at
FROM t_reconciliation_diff
WHERE id = :diffId AND
  /*bone:tenant*/
  AND deleted = 0
