SELECT
       id,
       task_no,
       claim_id,
       task_type,
       status,
       assignee_type,
       assignee_id,
       assign_dept_id,
       assign_rule_id,
       assign_reason,
       priority,
       claim_type,
       source_channel,
       claim_amount,
       assigned_at,
       claimed_at,
       finished_at,
       due_at,
       version,
       created_at,
       updated_at
FROM t_claim_task
WHERE claim_id = :claimId
  AND task_type = :taskType
  AND status IN ('PENDING_CLAIM','ASSIGNED','PROCESSING')
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
LIMIT 1
