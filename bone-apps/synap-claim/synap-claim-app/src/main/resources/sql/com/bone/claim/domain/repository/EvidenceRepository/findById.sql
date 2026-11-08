SELECT
       id,
       claim_id,
       doc_type,
       file_ref,
       content_hash,
       ocr_status,
       ocr_confidence,
       verify_valid,
       extracted_json,
       locked,
       locked_at,
       tenant_id,
       created_by,
       created_at,
       updated_by,
       updated_at,
       version,
       deleted
FROM t_claim_evidence
WHERE id = :evidenceId AND
  /*bone:tenant*/
  AND deleted = 0
