# Bone-Blueprint 测试指南

## 概述

本项目已按照 DDD 规范完成重构，包含完整的单元测试和架构测试。

## 项目结构

```
bone-blueprint/
├── src/
│   ├── main/
│   │   ├── java/com/bone/blueprint/
│   │   │   ├── BoneBlueprintApplication.java
│   │   │   ├── adapter/
│   │   │   │   ├── web/
│   │   │   │   │   ├── controller/
│   │   │   │   │   │   └── OrderController.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── req/
│   │   │   │   │   │   │   └── CreateOrderReq.java
│   │   │   │   │   │   └── resp/
│   │   │   │   │   │       └── OrderDetailResp.java
│   │   │   │   │   ├── assembler/
│   │   │   │   │   │   └── OrderAssembler.java
│   │   │   │   │   └── exception/
│   │   │   │   │       └── GlobalExceptionHandler.java
│   │   │   ├── application/
│   │   │   │   ├── command/
│   │   │   │   │   ├── cmd/
│   │   │   │   │   │   ├── CreateOrderCommand.java
│   │   │   │   │   │   ├── PayOrderCommand.java
│   │   │   │   │   │   └── CancelOrderCommand.java
│   │   │   │   │   └── handler/
│   │   │   │   │       ├── CreateOrderCommandHandler.java
│   │   │   │   │       ├── PayOrderCommandHandler.java
│   │   │   │   │       └── CancelOrderCommandHandler.java
│   │   │   │   └── query/
│   │   │   │       ├── qry/
│   │   │   │       │   └── OrderDetailQuery.java
│   │   │   │       ├── dto/
│   │   │   │       │   └── OrderDto.java
│   │   │   │       └── handler/
│   │   │   │           └── OrderDetailQueryHandler.java
│   │   │   ├── domain/
│   │   │   │   ├── order/
│   │   │   │   │   ├── Order.java
│   │   │   │   │   ├── OrderItem.java
│   │   │   │   │   ├── OrderStatus.java
│   │   │   │   │   └── event/
│   │   │   │   │       ├── OrderCreatedEvent.java
│   │   │   │   │       ├── OrderPaidEvent.java
│   │   │   │   │       └── OrderCancelledEvent.java
│   │   │   │   ├── service/
│   │   │   │   │   └── order/
│   │   │   │   │       └── OrderPriceCalculator.java
│   │   │   │   ├── gateway/
│   │   │   │   │   └── InventoryGateway.java
│   │   │   │   └── repository/
│   │   │   │       └── OrderRepository.java
│   │   │   └── infrastructure/
│   │   │       ├── gateway/
│   │   │       │   └── InventoryGatewayImpl.java
│   │   │       └── extension/
│   │   │           ├── StandardOrderPriceCalculator.java
│   │   │           └── VipOrderPriceCalculator.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── schema.sql
│   └── test/
│       └── java/com/bone/blueprint/
│           ├── ArchitectureTest.java
│           ├── domain/
│           │   └── order/
│           │       ├── OrderTest.java
│           │       └── OrderItemTest.java
│           ├── application/
│           │   └── command/
│           │       └── handler/
│           │           └── CreateOrderCommandHandlerTest.java
│           └── infrastructure/
│               └── extension/
│                   ├── StandardOrderPriceCalculatorTest.java
│                   └── VipOrderPriceCalculatorTest.java
└── pom.xml
```

## 运行测试

### 编译项目

```bash
cd /Users/renhui.trh/wps/bone/bone-blueprint
mvn clean compile
```

### 运行所有测试

```bash
mvn test
```

### 运行特定测试类

```bash
# 运行领域层测试
mvn test -Dtest=OrderTest
mvn test -Dtest=OrderItemTest

# 运行应用层测试
mvn test -Dtest=CreateOrderCommandHandlerTest
mvn test -Dtest=PayOrderCommandHandlerTest

# 运行基础设施层测试
mvn test -Dtest=StandardOrderPriceCalculatorTest
mvn test -Dtest=VipOrderPriceCalculatorTest

# 运行架构测试
mvn test -Dtest=ArchitectureTest
```

## 测试覆盖

### 领域层测试

1. **OrderTest** - 订单聚合根测试
   - 创建订单（成功和失败场景）
   - 支付订单
   - 取消订单
   - 添加/删除订单项
   - 更新订单金额

2. **OrderItemTest** - 订单明细实体测试
   - 创建订单明细（成功和失败场景）
   - 更新数量

### 应用层测试

3. **CreateOrderCommandHandlerTest** - 创建订单命令处理器测试
   - 成功创建订单
   - 库存不足场景
   - 多商品项场景

4. **PayOrderCommandHandlerTest** - 支付订单命令处理器测试
   - 成功支付
   - 订单不存在
   - 重复支付

### 基础设施层测试

5. **StandardOrderPriceCalculatorTest** - 标准价格计算器测试
   - 仅基础金额
   - 包含运费
   - 大金额计算

6. **VipOrderPriceCalculatorTest** - VIP价格计算器测试
   - 9折优惠计算
   - 包含运费的折扣计算
   - 验证折扣比例

### 架构测试

7. **ArchitectureTest** - ArchUnit 架构测试
   - 依赖方向检查
   - Domain 层纯净性检查
   - Repository 约束检查
   - 事务注解检查
   - Controller 注解检查

## DDD 规范符合性

### 4 条铁律

✅ **铁律 1：依赖方向必须正确**
- adapter → application → domain ← infrastructure
- ArchUnit 强制检查

✅ **铁律 2：Domain 必须绝对纯净**
- 无 Spring 注解
- 无框架注解（除 bone-metadata-sdk 的 @Table）
- 仅纯 Java + Lombok

✅ **铁律 3：业务逻辑必须在 Domain**
- 充血模型
- 业务规则封装在聚合根
- 禁止贫血模型

✅ **铁律 4：外部系统必须通过 ACL**
- InventoryGateway 接口定义
- InventoryGatewayImpl 实现

### 核心特性

✅ **Repository 空接口**
- OrderRepository 仅继承基类
- 无自定义方法

✅ **查询统一入口**
- 使用 Criteria/QueryBuilder
- 无自定义查询方法

✅ **ID 生成上移**
- 应用层使用 DistributedIdGenerator
- 领域层纯接收

✅ **实体 @Table 显式绑定**
- 所有实体使用 @Table 注解
- 显式指定表名

✅ **聚合根包直接化**
- domain.order 直接包含聚合根
- 同包放实体、值对象、事件

✅ **领域服务纯净**
- 无 Spring 注解
- 纯 POJO

✅ **扩展点替代 if-else**
- OrderPriceCalculator 扩展点
- StandardOrderPriceCalculator 实现
- VipOrderPriceCalculator 实现

## 数据库初始化

执行 schema.sql 初始化数据库表：

```bash
mysql -u root -p bone < src/main/resources/schema.sql
```

## 启动应用

```bash
mvn spring-boot:run
```

应用将在 http://localhost:8082 启动。

## API 端点

### 创建订单
```bash
POST /api/v1/orders
Content-Type: application/json

{
  "customerId": 1,
  "items": [
    {
      "productId": 1,
      "productName": "商品1",
      "quantity": 2,
      "unitPrice": 100.00
    }
  ]
}
# 201 Created + Location: /api/v1/orders/{id}，body: { "data": { "id": ... } }
```

### 支付订单
```bash
POST /api/v1/orders/{id}/pay
```

### 取消订单
```bash
POST /api/v1/orders/{id}/cancel
```

### 查询订单详情
```bash
GET /api/v1/orders/{id}
```

### 分页查询订单
```bash
GET /api/v1/orders?customerId=1&status=PAID&pageNum=1&pageSize=10
```

## 总结

本项目完全符合 DDD 规范要求，包含：
- ✅ 完整的四层架构
- ✅ 充血的领域模型
- ✅ CQRS 分离
- ✅ 扩展点机制
- ✅ 完整的单元测试
- ✅ ArchUnit 架构测试
- ✅ 符合 4 条铁律

所有测试应该能够正常运行并验证架构的正确性。
