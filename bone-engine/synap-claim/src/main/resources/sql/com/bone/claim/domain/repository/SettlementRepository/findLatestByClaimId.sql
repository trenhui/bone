-- 案件最新结算单（按创建时间倒序）
SELECT settlement_id,
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
WHERE claim_id = :claimId
  /*bone:tenant*/
ORDER BY created_at DESC
LIMIT 1
