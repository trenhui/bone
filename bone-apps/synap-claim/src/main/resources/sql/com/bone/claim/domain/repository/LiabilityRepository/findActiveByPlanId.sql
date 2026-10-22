-- 计划下生效责任（租户锚点由 SDK 注入）
SELECT id,
       liability_uuid,
       liability_version,
       plan_id,
       liability_code,
       liability_type,
       pay_percent,
       liability_deduct,
       liability_limit,
       social_only,
       waiting_period,
       status,
       tenant_id,
       created_at,
       updated_at,
       deleted
FROM t_adj_liability
WHERE plan_id = :planId
  AND status = 'ACTIVE'
  AND deleted = 0
  /*bone:tenant*/
ORDER BY id
