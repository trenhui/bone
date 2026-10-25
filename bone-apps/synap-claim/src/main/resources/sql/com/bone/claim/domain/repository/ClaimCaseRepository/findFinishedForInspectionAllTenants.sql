-- 后督抽样候选（全租户，质检调度 Job 专用）：status = FINISHED，按创建时间倒序取前 :limit 条
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
SELECT tenant_id,
       claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE deleted = 0
  AND status = 'FINISHED'
ORDER BY created_at DESC
LIMIT :limit
