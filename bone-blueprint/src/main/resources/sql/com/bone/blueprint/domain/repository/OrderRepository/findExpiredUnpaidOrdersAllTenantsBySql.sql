SELECT
    tenant_id,
    id AS order_id,
    customer_id,
    total_amount,
    status,
    created_at
FROM t_order
WHERE deleted = 0
  AND status = 'CREATED'
  AND created_at < #{before}
