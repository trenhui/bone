SELECT
       id,
       policy_no,
       policy_name,
       insurer_name,
       branch_name,
       insure_name,
       biz_identity_code,
       product_type,
       policy_type,
       effective_start,
       effective_end,
       waiting_period,
       policy_version,
       insured_snapshot,
       doc_claim_apply,
       doc_claim_notice,
       config_status,
       stale_flag,
       synced_at,
       tenant_id,
       created_at,
       updated_at,
       version,
       deleted
FROM t_claim_policy
WHERE policy_no = :policyNo
  AND deleted = 0 AND
  /*bone:tenant*/
LIMIT 1
