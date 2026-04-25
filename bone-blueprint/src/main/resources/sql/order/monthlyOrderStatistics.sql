SELECT
    DATE_FORMAT(create_time, '%Y-%m') as month,
    COUNT(*) as order_count,
    SUM(total_amount) as total_amount
FROM
    t_order
WHERE
    create_time >= DATE_SUB(NOW(), INTERVAL 12 MONTH)
AND
    deleted = 0
GROUP BY
    DATE_FORMAT(create_time, '%Y-%m')
ORDER BY
    month DESC
