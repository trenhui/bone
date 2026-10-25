-- 未终结案件扫描（全租户，RefreshLimitHourJob 专用）：非终态（status ∉ CLOSED_REJECTED/FINISHED）
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
SELECT tenant_id,
       claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE deleted = 0
  AND status NOT IN ('CLOSED_REJECTED', 'FINISHED')
ORDER BY id
LIMIT :limit
