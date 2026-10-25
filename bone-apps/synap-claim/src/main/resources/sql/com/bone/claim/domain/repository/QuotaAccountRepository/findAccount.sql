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
WHERE insured_id_hash = :insuredIdHash
  AND policy_no = :policyNo
  AND plan_id = :planId
  AND quota_year = :quotaYear
  AND quota_type = :quotaType
  AND liability_uuid = :liabilityUuid
  AND deleted = 0 AND
  /*bone:tenant*/
LIMIT 1
