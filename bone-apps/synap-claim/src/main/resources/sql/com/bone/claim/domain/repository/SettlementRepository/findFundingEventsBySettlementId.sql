SELECT
       id,
       settlement_id,
       settlement_no,
       claim_id,
       stage,
       amount,
       operator,
       account_type,
       idempotency_key,
       event_time,
       tenant_id,
       created_at
FROM settle_funding_event
WHERE settlement_id = :settlementId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY id
