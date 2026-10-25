SELECT
       tenant_id,
       id AS claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE status = '50'
ORDER BY created_at DESC
LIMIT :limit
