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
       created_by,
       created_at,
       updated_by,
       updated_at,
       version,
       deleted
FROM t_adj_calculation
WHERE id = :calculationId AND
  /*bone:tenant*/
