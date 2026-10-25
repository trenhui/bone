SELECT
       tenant_id,
       id AS claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE status = '21'
  AND updated_at < :before
