# Bone Blueprint 数据库演示 - 示例输出

## 完整的数据库读写演示

```
========================================
   Bone Blueprint 数据库演示程序
========================================

[步骤 1] 初始化数据库...
   ✓ 数据库初始化成功

[步骤 2] 初始化仓储...
   ✓ 仓储初始化成功

[步骤 3] 创建订单...
   ✓ 订单创建成功!
   • 订单ID: 1
   • 订单号: 20260425143000-1430-123
   • 客户ID: 1
   • 总金额: 250.00
   • 订单状态: PENDING
   • 订单项数量: 2

   [数据库写入]
   INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
   VALUES ('20260425143000-1430-123', 1, 250.00, 'PENDING', '2026-04-25T14:30:00Z', '2026-04-25T14:30:00Z')

   INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
   VALUES (1, 1, 2, 100.00, 200.00)

   INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
   VALUES (1, 2, 1, 50.00, 50.00)

[步骤 4] 查询订单（按ID）...
   ✓ 查询成功!
   • 订单ID: 1
   • 订单号: 20260425143000-1430-123
   • 客户ID: 1
   • 总金额: 250.00
   • 订单状态: PENDING

   [数据库读取]
   SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
   FROM "order"
   WHERE id = 1

[步骤 5] 查询订单及其订单项...
   ✓ 查询成功!
   • 订单ID: 1
   • 订单项:
      [1] 产品ID: 1, 数量: 2, 单价: 100.00, 小计: 200.00
      [2] 产品ID: 2, 数量: 1, 单价: 50.00, 小计: 50.00

   [数据库读取]
   SELECT id, order_id, product_id, quantity, price, subtotal
   FROM order_item
   WHERE order_id = 1

[步骤 6] 更新订单（支付）...
   ✓ 订单更新成功!
   • 新状态: PAID
   • 支付时间: 2026-04-25T14:30:01Z

   [数据库更新]
   UPDATE "order"
   SET status = 'PAID', update_time = '2026-04-25T14:30:01Z', pay_time = '2026-04-25T14:30:01Z', cancel_time = NULL
   WHERE id = 1

[步骤 7] 验证更新后的订单...
   ✓ 验证成功!
   • 当前状态: PAID
   • 支付时间: 2026-04-25T14:30:01Z

   [数据库读取]
   SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
   FROM "order"
   WHERE id = 1

[步骤 8] 创建更多订单...
   ✓ 订单2创建成功! ID: 2, 订单号: 20260425143002-1430-456, 金额: 160.00
   ✓ 订单3创建成功! ID: 3, 订单号: 20260425143003-1430-789, 金额: 480.00

   [数据库写入]
   INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
   VALUES ('20260425143002-1430-456', 1, 160.00, 'PENDING', '2026-04-25T14:30:02Z', '2026-04-25T14:30:02Z')

   INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
   VALUES (2, 3, 2, 80.00, 160.00)

   INSERT INTO "order" (order_no, customer_id, total_amount, status, create_time, update_time)
   VALUES ('20260425143003-1430-789', 1, 480.00, 'PENDING', '2026-04-25T14:30:03Z', '2026-04-25T14:30:03Z')

   INSERT INTO order_item (order_id, product_id, quantity, price, subtotal)
   VALUES (3, 4, 3, 160.00, 480.00)

[步骤 9] 按客户ID查询订单...
   ✓ 查询成功!
   • 客户ID: 1
   • 订单数量: 3
   • 订单列表:
      [1] 订单ID: 3, 订单号: 20260425143003-1430-789, 金额: 480.00, 状态: PENDING
      [2] 订单ID: 2, 订单号: 20260425143002-1430-456, 金额: 160.00, 状态: PENDING
      [3] 订单ID: 1, 订单号: 20260425143000-1430-123, 金额: 250.00, 状态: PAID

   [数据库读取]
   SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
   FROM "order"
   WHERE customer_id = 1
   ORDER BY create_time DESC
   LIMIT 10 OFFSET 0

[步骤 10] 统计客户订单数量...
   ✓ 统计成功!
   • 客户ID: 1
   • 订单总数: 3

   [数据库读取]
   SELECT COUNT(*) FROM "order" WHERE customer_id = 1

[步骤 11] 删除订单...
   ✓ 订单删除成功!
   • 订单ID: 1

   [数据库删除]
   DELETE FROM "order" WHERE id = 1

[步骤 12] 验证删除后的订单...
   ✓ 验证成功!
   • 订单已删除，查询结果为空

   [数据库读取]
   SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
   FROM "order"
   WHERE id = 1
   (返回空结果)

[步骤 13] 按订单号查询第二个订单...
   ✓ 查询成功!
   • 订单ID: 2
   • 订单号: 20260425143002-1430-456
   • 客户ID: 1
   • 总金额: 160.00
   • 订单状态: PENDING

   [数据库读取]
   SELECT id, order_no, customer_id, total_amount, status, create_time, update_time, pay_time, cancel_time
   FROM "order"
   WHERE order_no = '20260425143002-1430-456'

========================================
   演示完成!
========================================
```

## 数据库操作总结

### 1. 创建操作 (INSERT)
- 3个订单记录
- 4个订单项记录

### 2. 读取操作 (SELECT)
- 按ID查询订单 × 3次
- 查询订单及其订单项 × 1次
- 按客户ID查询订单列表 × 1次
- 统计订单数量 × 1次
- 按订单号查询订单 × 1次

### 3. 更新操作 (UPDATE)
- 更新订单状态 × 1次

### 4. 删除操作 (DELETE)
- 删除订单 × 1次（级联删除相关订单项）

## 数据库状态

### 最终订单表 ("order")
| id | order_no | customer_id | total_amount | status | create_time | update_time | pay_time | cancel_time |
|----|----------|-------------|--------------|--------|-------------|-------------|----------|-------------|
| 2 | 20260425143002-1430-456 | 1 | 160.00 | PENDING | 2026-04-25T14:30:02Z | 2026-04-25T14:30:02Z | NULL | NULL |
| 3 | 20260425143003-1430-789 | 1 | 480.00 | PENDING | 2026-04-25T14:30:03Z | 2026-04-25T14:30:03Z | NULL | NULL |

### 最终订单项表 (order_item)
| id | order_id | product_id | quantity | price | subtotal |
|----|----------|------------|----------|-------|----------|
| 3 | 2 | 3 | 2 | 80.00 | 160.00 |
| 4 | 3 | 4 | 3 | 160.00 | 480.00 |

## 功能特点

1. **事务支持** - 使用数据库事务确保订单和订单项的一致性
2. **完整CRUD** - 支持创建、读取、更新、删除操作
3. **关系查询** - 支持查询订单及其关联的订单项
4. **分页查询** - 支持按条件分页查询订单
5. **统计查询** - 支持统计特定条件的订单数量
6. **软删除兼容** - 支持软删除（虽然本演示使用硬删除）
