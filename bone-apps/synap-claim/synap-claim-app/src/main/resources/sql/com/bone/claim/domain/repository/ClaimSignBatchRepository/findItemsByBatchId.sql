SELECT i.id, i.tenant_id, i.batch_id, i.claim_id, i.claim_no, i.image_ready,
       i.policy_no, i.plan_id, i.insured_name, i.insured_id_no,
       i.identity_type, i.counting_result, i.image_count, i.image_upload_count,
       i.image_upload_time, i.image_uploader,
       i.out_insure_time, i.claim_type, i.source_channel, i.business_key,
       i.case_status, i.created_case_id
FROM t_claim_sign_batch_item i
WHERE i.batch_id = :batchId
  AND i.deleted = 0
  AND /*bone:tenant*/
ORDER BY i.id
