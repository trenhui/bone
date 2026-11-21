SELECT COUNT(*)
FROM t_claim_case
WHERE second_reviewer_id IS NOT NULL
  AND second_reviewer_id <> ''
  AND status IN ('REVIEWING', 'RE_REVIEW_PENDING', 'RE_REVIEW_PASSED')
  AND deleted = 0
  AND created_at >= :from
  AND created_at < :to
  AND /*bone:tenant*/
