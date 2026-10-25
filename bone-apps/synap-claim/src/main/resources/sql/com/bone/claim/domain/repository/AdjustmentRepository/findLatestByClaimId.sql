SELECT
       id,
       claim_id,
       liability_uuid,
       liability_version,
       rule_version,
       formula_snapshot,
       formula_hash,
       total_amount,
       result_status,
       tenant_id,
       created_at,
       updated_at,
       version
FROM t_adj_calculation
WHERE claim_id = :claimId AND
  /*bone:tenant*/
ORDER BY created_at DESC
LIMIT 1
