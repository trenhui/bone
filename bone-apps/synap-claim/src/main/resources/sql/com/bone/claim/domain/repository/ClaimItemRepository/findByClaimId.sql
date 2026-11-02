SELECT
       id,
       claim_id,
       item_name,
       item_code,
       category,
       fee_code,
       quantity,
       unit_price,
       amount,
       social_flag,
       medical_type,
       pooling_amount,
       self_pay_1,
       self_pay_2,
       self_pay_c,
       third_party_paid,
       department,
       valid_amount,
       invalid_amount,
       tenant_id,
       created_at,
       updated_at,
       version,
       deleted
FROM t_claim_item
WHERE claim_id = :claimId
  AND deleted = 0 AND
  /*bone:tenant*/
ORDER BY id
