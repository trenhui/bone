SELECT
       id,
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
WHERE quota_account_id = :quotaAccountId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY id
