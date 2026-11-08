SELECT
       id,
       task_no,
       claim_id,
       assignee_id,
       status,
       label_payload_json,
       payload_hash,
       label_result_json,
       submitted_at,
       ai_suggestion_json,
       agreement,
       unblinded_at,
       tenant_id,
       created_at,
       updated_at,
       version
FROM t_claim_blind_label_task
WHERE claim_id = :claimId AND
  status IN ('PENDING', 'LABELED') AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY created_at DESC
LIMIT 1
