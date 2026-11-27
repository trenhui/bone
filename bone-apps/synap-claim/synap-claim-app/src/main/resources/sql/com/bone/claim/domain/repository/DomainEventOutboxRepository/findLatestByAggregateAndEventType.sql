SELECT id,
       tenant_id,
       aggregate,
       aggregate_id,
       event_type,
       payload,
       status,
       retry_count,
       next_retry_at,
       created_at,
       created_by,
       updated_at,
       updated_by,
       deleted,
       version
FROM t_domain_event_outbox
WHERE aggregate = :aggregate
  AND aggregate_id = :aggregateId
  AND event_type = :eventType
  AND deleted = 0
  AND /*bone:tenant*/
ORDER BY created_at DESC
LIMIT 1
