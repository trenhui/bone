SELECT
    o.id AS order_id,
    o.customer_id,
    o.total_amount,
    o.status,
    o.created_at,
    o.channel_source,
    oi.id AS item_id,
    oi.product_id,
    oi.product_name,
    oi.quantity,
    oi.unit_price,
    oi.subtotal,
    (SELECT MAX(p.id) FROM bp_payment p WHERE p.order_id = o.id AND p.deleted = 0) AS payment_id
FROM t_order o
LEFT JOIN t_order_item oi ON o.id = oi.order_id AND oi.deleted = 0
WHERE o.id = #{orderId}
  AND /*bone:tenant*/
  AND o.deleted = 0
ORDER BY oi.id
