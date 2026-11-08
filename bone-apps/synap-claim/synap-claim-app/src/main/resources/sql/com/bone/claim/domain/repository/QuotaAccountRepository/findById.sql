SELECT
       id,
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
       tenant_id,
       created_by,
       created_at,
       updated_by,
       updated_at,
       version,
       deleted
FROM t_quota_account
WHERE id = :quotaAccountId AND
  /*bone:tenant*/
  AND deleted = 0
