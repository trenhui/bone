-- 额度流水（守恒校验与审计；append-only，只追加不更新）
SELECT id,
       quota_account_id,
       claim_id,
       settlement_no,
       direction,
       txn_type,
       amount,
       before_amount,
       after_amount,
       idempotency_key,
       occurred_at,
       tenant_id,
       created_at
FROM t_quota_transaction
WHERE quota_account_id = :quotaAccountId
  /*bone:tenant*/
ORDER BY id
