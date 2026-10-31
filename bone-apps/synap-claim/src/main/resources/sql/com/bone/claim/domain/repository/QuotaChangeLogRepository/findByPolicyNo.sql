SELECT
       id,
       quota_account_id,
       policy_no,
       insured_id_hash,
       change_type,
       before_initial,
       after_initial,
       operator_id,
       operator_name,
       remark,
       occurred_at,
       tenant_id,
       created_at,
       updated_at,
       deleted
FROM t_quota_change_log
WHERE policy_no = :policyNo
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
