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
       biz_date,
       tenant_id,
       created_at,
       updated_at
FROM t_quota_transaction
WHERE
  /*bone:tenant*/
  AND deleted = 0
ORDER BY quota_account_id, id
