SELECT
    o.id as order_id,
    o.customer_id,
    o.total_amount,
    o.status,
    o.create_time,
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
    o.id = #{orderId}
AND
    o.deleted = 0
