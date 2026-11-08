SELECT
       tenant_id,
       id AS claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE status = 'EVIDENCE_SUBMITTING'
  AND updated_at < :before
  AND deleted = 0
