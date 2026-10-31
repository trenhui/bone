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
       created_at,
       updated_at,
       version,
       deleted
FROM t_quota_account
WHERE policy_no = :policyNo
  AND insured_id_hash = :insuredIdHash
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
