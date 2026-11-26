INSERT INTO t_claim_sign_track (id, tenant_id, batch_id, claim_id, sign_status, action_type, message, remark, operator, occurred_at, created_by, updated_by, created_at, updated_at, deleted)
VALUES (:id, :tenantId, :batchId, :claimId, :signStatus, :actionType, :message, :remark, :operator, :occurredAt, NULL, NULL, NOW(3), NOW(3), 0)
