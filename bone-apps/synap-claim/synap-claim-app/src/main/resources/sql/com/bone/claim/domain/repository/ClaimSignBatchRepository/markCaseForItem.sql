UPDATE t_claim_sign_batch_item i
SET i.case_status = :status,
    i.updated_at = NOW(3)
WHERE i.id = :id
  AND i.tenant_id = :tenantId
  AND i.deleted = 0
