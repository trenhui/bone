-- 按结算单号查（幂等去重入口）
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
WHERE settlement_no = :settlementNo
  /*bone:tenant*/
LIMIT 1
