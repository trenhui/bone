SELECT tenant_id,
       claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE deleted = 0
  AND status NOT IN ('CLOSED_REJECTED', 'FINISHED')
ORDER BY id
LIMIT :limit
