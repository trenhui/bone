-- 待投发件箱扫描（全租户，OutboxRelayJob 专用）：PENDING 且超过窗口，按时间升序取一批
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
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
