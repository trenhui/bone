SELECT COUNT(*)
FROM t_domain_event_outbox
WHERE status = :status
  AND deleted = 0
  AND created_at >= :from
  AND created_at < :to
  AND /*bone:tenant*/
