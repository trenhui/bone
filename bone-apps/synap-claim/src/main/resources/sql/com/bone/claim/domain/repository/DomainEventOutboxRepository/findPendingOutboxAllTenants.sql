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
WHERE status = 'PENDING'
  AND created_at <= :before
ORDER BY created_at ASC
LIMIT :limit
