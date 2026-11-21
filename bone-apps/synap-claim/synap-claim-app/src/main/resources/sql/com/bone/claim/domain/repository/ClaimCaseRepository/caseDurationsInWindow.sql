SELECT id AS claim_id, TIMESTAMPDIFF(MILLISECOND, created_at, closed_at) AS duration_ms
FROM t_claim_case
WHERE closed_at IS NOT NULL
  AND closed_at >= :from
  AND closed_at < :to
  AND status IN ('FINISHED', 'CLOSED_REJECTED')
  AND deleted = 0
  AND /*bone:tenant*/
