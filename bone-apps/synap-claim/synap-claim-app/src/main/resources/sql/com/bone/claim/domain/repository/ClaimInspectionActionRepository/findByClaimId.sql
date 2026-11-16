SELECT id, tenant_id, claim_id, task_id, inspector_id, decision, back_reason,
       reversal_verified, feature_enabled, created_by, created_at, updated_by, updated_at, deleted
FROM t_claim_inspection_action
WHERE claim_id = :claimId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY created_at DESC, id DESC
