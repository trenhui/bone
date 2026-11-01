SELECT
       d.id            AS diff_id,
       d.report_id     AS report_id,
       r.biz_date      AS biz_date,
       r.channel       AS channel,
       d.claim_no      AS claim_no,
       d.settlement_no AS settlement_no,
       d.diff_type     AS diff_type,
       d.local_amount  AS local_amount,
       d.remote_amount AS remote_amount,
       d.local_stage   AS local_stage,
       d.remote_status AS remote_status,
       d.created_at    AS created_at
FROM t_reconciliation_diff d
       LEFT JOIN t_reconciliation_report r ON r.id = d.report_id AND r.deleted = 0
       LEFT JOIN t_reconciliation_action a
              ON a.target_type = 'DIFF' AND a.target_id = d.id AND a.action_type = 'DIFF_RESOLVE'
WHERE a.id IS NULL AND
  d.deleted = 0 AND
  /*bone:tenant*/
ORDER BY d.created_at DESC, d.id DESC
LIMIT :limit
