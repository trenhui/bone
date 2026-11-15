SELECT DISTINCT
       c.id          AS claim_id,
       c.claim_no    AS claim_no,
       c.policy_no   AS policy_no,
       c.status      AS status,
       c.stage       AS stage,
       c.created_at  AS created_at,
       CASE
         WHEN c.claim_no LIKE :pattern ESCAPE '!' THEN 'CLAIM_NO'
         WHEN c.policy_no LIKE :pattern ESCAPE '!' THEN 'POLICY_NO'
         WHEN c.invoice_no LIKE :pattern ESCAPE '!' THEN 'INVOICE_NO'
         WHEN c.invoice_code LIKE :pattern ESCAPE '!' THEN 'INVOICE_CODE'
         WHEN c.batch_no LIKE :pattern ESCAPE '!' THEN 'BATCH_NO'
         WHEN s.name_masked LIKE :pattern ESCAPE '!' THEN 'NAME_MASKED'
         WHEN s.phone_masked LIKE :pattern ESCAPE '!' THEN 'PHONE_MASKED'
         WHEN s.person_id_hash LIKE :pattern ESCAPE '!' THEN 'PERSON_ID_HASH'
         ELSE 'OTHER'
       END           AS matched_key
FROM t_claim_case c
       LEFT JOIN t_claim_stakeholder s
         ON s.claim_id = c.id
        AND s.tenant_id = c.tenant_id
        AND s.deleted = 0
WHERE (
       c.claim_no LIKE :pattern ESCAPE '!'
    OR c.policy_no LIKE :pattern ESCAPE '!'
    OR c.invoice_no LIKE :pattern ESCAPE '!'
    OR c.invoice_code LIKE :pattern ESCAPE '!'
    OR c.batch_no LIKE :pattern ESCAPE '!'
    OR c.insured_id_hash LIKE :pattern ESCAPE '!'
    OR s.name_masked LIKE :pattern ESCAPE '!'
    OR s.phone_masked LIKE :pattern ESCAPE '!'
    OR s.person_id_hash LIKE :pattern ESCAPE '!'
  )
  AND /*bone:tenant*/
  AND c.deleted = 0
ORDER BY c.created_at DESC
LIMIT :limit
