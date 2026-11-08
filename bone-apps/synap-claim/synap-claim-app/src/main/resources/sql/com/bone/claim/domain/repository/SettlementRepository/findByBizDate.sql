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
       review_first_approver,
       tenant_id,
       created_at,
       updated_at,
       version
FROM settle_order
WHERE deleted = 0
  AND DATE(created_at) = :bizDate AND
  /*bone:tenant*/
ORDER BY id
