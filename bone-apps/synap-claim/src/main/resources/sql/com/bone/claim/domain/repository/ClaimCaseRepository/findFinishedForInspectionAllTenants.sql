-- 后督抽样候选（全租户，质检调度 Job 专用）：status = FINISHED，按创建时间倒序取前 :limit 条
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
SELECT
       tenant_id,
       id AS claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE status = 'FINISHED'
  AND deleted = 0
ORDER BY created_at DESC
LIMIT :limit
