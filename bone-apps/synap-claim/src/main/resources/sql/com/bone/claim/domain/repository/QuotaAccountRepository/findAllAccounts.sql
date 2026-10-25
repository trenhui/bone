-- 本租户全部额度账户（对账守恒核对；AUTO 租户锚点由 SDK 注入）
SELECT id,
       insured_id_hash,
       policy_no,
       plan_id,
       quota_year,
       quota_type,
       liability_uuid,
       quota_initial,
       quota_claimed,
       quota_frozen,
       deductible_used,
       cap_used,
       insured_state,
       version,
       tenant_id,
       created_at,
       updated_at,
       created_by,
       updated_by,
       deleted
FROM t_quota_account
WHERE deleted = 0
  /*bone:tenant*/
ORDER BY id
