SELECT
       id,
       liability_uuid,
       liability_version,
       plan_id,
       liability_code,
       liability_type,
       pay_percent,
       liability_deduct,
       liability_limit,
       invoice_deduct,
       adjust_deduct,
       daily_limit,
       daily_limit_basis,
       limit_mode,
       limit_hospital_codes,
       limit_diagnosis_codes,
       catalog_policy,
       catalog_code,
       social_only,
       waiting_period,
       status,
       tenant_id,
       created_at,
       updated_at,
       version,
       deleted
FROM t_adj_liability
WHERE id = :id
  AND deleted = 0 AND
  /*bone:tenant*/
LIMIT 1
