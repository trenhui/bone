SELECT
       id,
       biz_identity_code,
       coverage_uuid,
       override_field,
       standard_value,
       override_value,
       status,
       version,
       tenant_id,
       created_at,
       updated_at,
       deleted
FROM t_claim_coverage_override
WHERE id = :id
  AND deleted = 0 AND
  /*bone:tenant*/
