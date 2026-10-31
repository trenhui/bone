SELECT id,
       settlement_no,
       claim_id,
       claim_no,
       amount,
       funding_stage,
       account_type,
       attempt,
       paid_at,
       status,
       tenant_id,
       created_at,
       updated_at,
       version
FROM settle_order
WHERE deleted = 0
  AND DATE(created_at) = :bizDate
  /*bone:tenant*/
ORDER BY id
