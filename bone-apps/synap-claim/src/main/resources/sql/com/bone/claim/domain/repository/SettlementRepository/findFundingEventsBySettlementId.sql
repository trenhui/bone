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
ORDER BY id
