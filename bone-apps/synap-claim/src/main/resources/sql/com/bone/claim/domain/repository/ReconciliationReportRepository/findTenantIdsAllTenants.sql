-- 需对账的租户（额度账户 ∪ 结算单的 distinct tenant_id）
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
SELECT DISTINCT tenant_id
FROM (
  SELECT tenant_id FROM t_quota_account WHERE deleted = 0
  UNION
  SELECT tenant_id FROM settle_order WHERE deleted = 0
) t
ORDER BY tenant_id
