SELECT
       c.tenant_id,
       c.id AS claim_id,
       c.claim_no,
       c.status
FROM t_claim_case c
WHERE c.status = 'FINISHED'
  AND c.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM t_claim_inspection i
    WHERE i.claim_id = c.id
      AND i.tenant_id = c.tenant_id
      AND i.deleted = 0
  )
ORDER BY c.created_at DESC
LIMIT :limit
