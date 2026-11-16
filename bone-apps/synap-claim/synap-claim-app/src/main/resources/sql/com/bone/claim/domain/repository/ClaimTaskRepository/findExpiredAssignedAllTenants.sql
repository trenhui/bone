SELECT id, tenant_id, task_no, claim_id, task_type, status, assignee_type, assignee_id,
       assign_dept_id, assign_rule_id, assign_reason, priority, claim_type, source_channel,
       claim_amount, assigned_at, claimed_at, finished_at, due_at, version, created_at,
       updated_at, deleted
FROM t_claim_task
WHERE status = 'ASSIGNED'
  AND claimed_at IS NULL
  AND due_at IS NOT NULL
  AND due_at < :before
  AND deleted = 0
ORDER BY due_at ASC, id ASC
LIMIT :limit
