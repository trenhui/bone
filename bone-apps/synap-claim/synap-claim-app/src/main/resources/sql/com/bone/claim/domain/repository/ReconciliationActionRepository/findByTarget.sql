SELECT
       id,
       target_type,
       target_id,
       action_type,
       resolution,
       detail,
       operator_id,
       occurred_at,
       tenant_id,
       created_at
FROM t_reconciliation_action
WHERE target_type = :targetType AND
  target_id = :targetId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY occurred_at, id
