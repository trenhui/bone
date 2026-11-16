SELECT id, tenant_id, principal_id, agent_id, valid_from, valid_to, state, reason,
       created_by, created_at, updated_by, updated_at, deleted
FROM t_claim_user_agent
WHERE principal_id = :principalId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY valid_from DESC, id DESC
