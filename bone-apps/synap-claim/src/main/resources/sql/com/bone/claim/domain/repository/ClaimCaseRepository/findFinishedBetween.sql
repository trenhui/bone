SELECT
       c.id            AS claim_id,
       c.claim_no      AS claim_no,
       c.approved_amount AS approved_amount
FROM t_claim_case c
WHERE c.status = 'FINISHED'
  AND c.deleted = 0
  AND c.closed_at >= :from
  AND c.closed_at < :to
  /*bone:tenant*/
ORDER BY c.id
