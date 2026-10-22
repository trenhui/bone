-- 按责任 UUID + 版本查（复算/回放取历史版本）
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
WHERE liability_uuid = :liabilityUuid
  AND liability_version = :liabilityVersion
  AND deleted = 0
  /*bone:tenant*/
LIMIT 1
