SELECT
       id,
       calculation_id,
       liability_uuid,
       line_type,
       input_snapshot,
       fee_calculate,
       fee_deduct,
       ratio_payout,
       fee_occupied,
       fee_payout,
       fee_remain,
       zero_reason,
       reject_type,
       formula_hash,
       tenant_id,
       created_at,
       updated_at
FROM t_adj_calculation_line
WHERE calculation_id = :calculationId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
