SELECT
       id,
       plan_id,
       coverage_code,
       coverage_name,
       coverage_limit,
       liability_type,
       status,
       tenant_id,
       created_at,
       updated_at,
       version,
       deleted
FROM t_claim_coverage
WHERE plan_id = :planId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
