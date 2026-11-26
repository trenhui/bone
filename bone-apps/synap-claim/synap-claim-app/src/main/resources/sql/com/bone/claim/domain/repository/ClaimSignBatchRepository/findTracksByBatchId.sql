SELECT t.id, t.tenant_id, t.batch_id, t.claim_id, t.sign_status, t.action_type,
       t.message, t.remark, t.operator, t.occurred_at
FROM t_claim_sign_track t
WHERE t.batch_id = :batchId
  AND t.deleted = 0
  AND /*bone:tenant*/
ORDER BY t.occurred_at, t.id
