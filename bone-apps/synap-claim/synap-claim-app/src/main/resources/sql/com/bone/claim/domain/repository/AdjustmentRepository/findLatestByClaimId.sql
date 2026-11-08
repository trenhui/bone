SELECT
       id,
       claim_id,
       liability_uuid,
       liability_version,
       rule_version,
       plan_version,
       catalog_version,
       model_version,
       prompt_version,
       graph_version,
       formula_snapshot,
       formula_hash,
       total_amount,
       result_status,
       shared_code,
       tenant_id,
       created_at,
       updated_at,
       version
FROM t_adj_calculation
WHERE claim_id = :claimId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY created_at DESC
LIMIT 1
