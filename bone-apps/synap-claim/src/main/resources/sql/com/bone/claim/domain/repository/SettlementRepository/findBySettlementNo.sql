SELECT
       id,
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
WHERE settlement_no = :settlementNo AND
  /*bone:tenant*/
  AND deleted = 0
LIMIT 1
