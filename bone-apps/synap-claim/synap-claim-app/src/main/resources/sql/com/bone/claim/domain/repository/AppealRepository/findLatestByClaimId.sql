SELECT
       id,
       appeal_no,
       claim_id,
       claim_no,
       original_status,
       status,
       submitted_by,
       submitted_at,
       case_closed_at,
       reviewed_by,
       reviewed_at,
       review_result,
       review_reason,
       tenant_id,
       created_at,
       updated_at,
       version
FROM appeal_record
WHERE claim_id = :claimId AND
  /*bone:tenant*/
  AND deleted = 0
ORDER BY created_at DESC
LIMIT 1
