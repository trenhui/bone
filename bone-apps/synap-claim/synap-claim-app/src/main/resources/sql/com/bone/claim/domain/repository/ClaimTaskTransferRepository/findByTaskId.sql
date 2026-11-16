SELECT id, tenant_id, task_id, claim_id, from_assignee_id, to_assignee_id, reason,
       transferred_by, created_by, created_at, updated_by, updated_at, deleted
FROM t_claim_task_transfer
WHERE task_id = :taskId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY created_at DESC, id DESC
