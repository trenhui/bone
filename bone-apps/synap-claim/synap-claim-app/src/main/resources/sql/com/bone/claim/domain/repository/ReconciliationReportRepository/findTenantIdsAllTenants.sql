SELECT DISTINCT tenant_id
FROM (
  SELECT tenant_id FROM t_quota_account WHERE deleted = 0
  UNION
  SELECT tenant_id FROM settle_order WHERE deleted = 0
) t
ORDER BY tenant_id
