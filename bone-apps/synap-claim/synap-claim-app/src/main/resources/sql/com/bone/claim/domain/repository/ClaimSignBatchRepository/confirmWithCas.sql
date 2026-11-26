UPDATE t_claim_sign_batch b
SET b.sign_status = 'FINISHED',
    b.sign_operator = :operator,
    b.sign_remark = :remark,
    b.sign_time = NOW(),
    b.updated_at = NOW()
WHERE b.id = :id
  AND b.tenant_id = :tenantId
  AND b.sign_status = :expectedStatus
  AND b.deleted = 0
