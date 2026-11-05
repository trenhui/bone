SELECT
       id,
       inspection_no,
       claim_id,
       claim_no,
       dimension,
       sample_stratum,
       inspect_type,
       status,
       inspector_id,
       inspected_at,
       result,
       diff_type,
       diff_detail,
       action_taken,
       target_status,
       tenant_id,
       created_at,
       updated_at,
       version
FROM t_claim_inspection
WHERE status = 'CONCLUDED' AND
  inspected_at IS NOT NULL AND
  inspected_at >= :from AND
  inspected_at < :to AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY inspected_at DESC, id DESC
LIMIT 500
