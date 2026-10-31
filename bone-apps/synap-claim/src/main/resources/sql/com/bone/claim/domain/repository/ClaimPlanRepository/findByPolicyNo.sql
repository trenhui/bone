SELECT
       id,
       policy_no,
       plan_code,
       plan_name,
       plan_limit,
       waiting_period,
       version_no,
       status,
       tenant_id,
       created_at,
       updated_at,
       version,
       deleted
FROM t_claim_plan
WHERE policy_no = :policyNo
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
