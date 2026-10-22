-- 资金流水（追加式不可变，对账与审计事实源）
SELECT event_id,
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
WHERE settlement_id = :settlementId
  /*bone:tenant*/
ORDER BY event_id
