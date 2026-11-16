SELECT
       CASE
         WHEN event_type = 'DISPOSITION'
           THEN CONCAT('DISPOSITION_', COALESCE(disposition, 'UNKNOWN'))
         ELSE event_type
       END           AS bucket,
       COUNT(*)      AS event_count
FROM t_claim_ai_feedback_event
WHERE created_at >= :since AND
  /*bone:tenant*/
  AND deleted = 0
GROUP BY bucket
