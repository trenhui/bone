-- 案件证据列表（租户锚点：SDK 自动注入 tenant_id = :__boneTenantId__）
SELECT id,
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
       created_at,
       updated_at,
       version,
       deleted
FROM t_claim_evidence
WHERE claim_id = :claimId
  AND deleted = 0
  /*bone:tenant*/
ORDER BY created_at
