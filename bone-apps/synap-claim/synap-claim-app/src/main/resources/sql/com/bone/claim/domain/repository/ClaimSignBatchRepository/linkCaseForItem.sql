UPDATE t_claim_sign_batch_item i
SET i.claim_id = :claimId,
    i.created_case_id = :claimId,
    i.case_status = :status,
    i.updated_at = NOW(3)
WHERE i.id = :id
  AND i.tenant_id = :tenantId
  AND i.deleted = 0
