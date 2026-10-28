-- 后督抽样候选（全租户，质检调度 Job 专用）：status = FINISHED，按创建时间倒序取前 :limit 条
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
--
-- 已抽检案件必须排除：按 created_at DESC 取前 N 条时，若不过滤已抽检案件，最老的一批会被
-- 「最新 N 条恒为已抽检」永久饿死（抽样覆盖率静默停在头部），且每轮重复扫同一批行白烧 DB。
SELECT
       c.tenant_id,
       c.id AS claim_id,
       c.claim_no,
       c.status
FROM t_claim_case c
WHERE c.status = 'FINISHED'
  AND c.deleted = 0
  AND NOT EXISTS (
    SELECT 1
    FROM t_claim_inspection i
    WHERE i.claim_id = c.id
      AND i.tenant_id = c.tenant_id
      AND i.deleted = 0
  )
ORDER BY c.created_at DESC
LIMIT :limit
