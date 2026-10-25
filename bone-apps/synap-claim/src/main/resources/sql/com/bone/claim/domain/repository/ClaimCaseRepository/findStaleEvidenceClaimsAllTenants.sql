-- 超期未补件候选（全租户，CloseExpiredClaimJob 专用）：status = EVIDENCE_SUBMITTING 且 updated_at < :before
-- 全租户扫描：不加 /*bone:tenant*/ 锚点（@TenantScope(ALL) 跨租户）
SELECT
       tenant_id,
       id AS claim_id,
       claim_no,
       status
FROM t_claim_case
WHERE status = 'EVIDENCE_SUBMITTING'
  AND updated_at < :before
  AND deleted = 0
