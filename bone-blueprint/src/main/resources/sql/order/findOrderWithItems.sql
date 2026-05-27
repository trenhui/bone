SELECT
    o.id as order_id,
    o.customer_id,
    o.total_amount,
    o.status,
    o.created_at,
    oi.id as item_id,
    oi.product_id,
    oi.product_name,
    oi.quantity,
    oi.unit_price,
    oi.subtotal
FROM
    t_order o
LEFT JOIN
    t_order_item oi ON o.id = oi.order_id
WHERE
    o.id = :orderId
AND
    o.tenant_id = :tenantId
AND
    o.deleted = 0
