# Bone 采购模块

Bone 采购模块是 Bone 引擎的核心组件之一，提供完整的采购订单管理功能，包括订单创建、审批流程、状态管理等。

## 模块结构

采购模块由以下两个主要子模块组成：

1. **bone-procurement-engine** - 核心引擎模块，包含业务规则和模型定义
2. **bone-procurement-service** - 服务实现模块，提供RESTful API和业务逻辑实现

## 主要功能

### 1. 采购订单管理
- 创建采购订单
- 更新采购订单
- 查询采购订单（按ID、编号、条件等）
- 取消采购订单
- 关闭采购订单

### 2. 审批流程
- 提交订单审批
- 多级审批处理
- 自动审批规则
- 紧急采购规则

### 3. 业务规则引擎
- 订单验证规则
- 审批需求判断
- 审批流程定义
- 审批级别确定
- 审批人分配
- 紧急采购处理

### 4. 与元数据引擎集成
- 利用 Bone 智能元数据引擎实现动态配置
- 支持多租户配置
- 支持元数据版本管理
- 支持元数据缓存优化

## 技术栈

- Java 11+
- Spring Boot 2.x
- Spring Data JPA
- Redis（缓存）
- RabbitMQ（消息队列）
- MySQL（数据库）

## 快速开始

### 环境要求

- JDK 11 或更高版本
- Maven 3.6+ 或 Gradle 6+
- MySQL 5.7+ 或 MariaDB 10.3+
- Redis 5.0+
- RabbitMQ 3.8+

### 配置文件

主要配置文件位于 `bone-procurement-service/src/main/resources/application.yml`，包含以下核心配置：

- 数据源配置
- JPA配置
- Redis配置
- RabbitMQ配置
- 元数据引擎配置
- 采购业务特定配置

### 启动服务

1. 确保所有依赖服务已启动（MySQL、Redis、RabbitMQ）
2. 运行 `ProcurementApplication` 类或使用以下Maven命令：

```bash
cd bone-procurement-service
mvn spring-boot:run
```

### API 文档

服务启动后，可以通过以下地址访问 Swagger API 文档：

```
http://localhost:8082/procurement/swagger-ui.html
```

## 核心接口

### 采购订单服务接口 (PurchaseOrderService)

提供采购订单的完整业务操作：

- `createOrder` - 创建采购订单
- `updateOrder` - 更新采购订单
- `getOrderById` - 根据ID获取订单
- `getOrderByCode` - 根据编号获取订单
- `findOrdersByConditions` - 条件查询订单
- `submitForApproval` - 提交审批
- `approveOrder` - 审批订单
- `cancelOrder` - 取消订单
- `closeOrder` - 关闭订单
- `validateOrder` - 验证订单
- `getOrderStatistics` - 获取订单统计

### 采购订单规则引擎接口 (PurchaseOrderRuleEngine)

处理采购订单的业务规则：

- `validateOrder` - 验证订单规则
- `requiresApproval` - 判断是否需要审批
- `getApprovalFlowDefinition` - 获取审批流程定义
- `getApprovalLevel` - 获取审批级别
- `getNextApprover` - 获取下一审批人
- `executeEmergencyPurchaseRule` - 执行紧急采购规则

## 数据模型

### 采购订单 (PurchaseOrder)

包含订单的基本信息、供应商信息、金额信息、明细列表等。

### 采购订单明细 (PurchaseOrderItem)

包含采购物品的详细信息，如商品编码、名称、数量、单价等。

### 规则验证结果 (RuleValidationResult)

存储规则验证的结果，包括规则ID、名称、状态、错误信息等。

### 规则执行上下文 (RuleExecutionContext)

提供规则执行的上下文环境，包含租户ID、用户ID、时间等信息。

### 审批结果 (ApprovalResult)

存储订单审批的结果，包括状态、审批人、级别、下一步信息等。

## 状态流转

采购订单的主要状态流转如下：

1. **DRAFT** - 草稿状态，可编辑
2. **PENDING_APPROVAL** - 待审批状态
3. **APPROVED** - 已审批状态
4. **REJECTED** - 已拒绝状态
5. **CANCELLED** - 已取消状态
6. **CLOSED** - 已关闭状态

## 审批流程

1. 订单提交审批
2. 根据规则判断是否需要审批
3. 如果金额小于阈值，自动审批通过
4. 如果需要审批，根据金额确定审批级别和审批人
5. 多级审批人依次审批
6. 所有审批通过后，订单状态变为已审批
7. 任一审批人拒绝，订单状态变为已拒绝

## 安全考虑

- 使用Spring Security进行认证和授权
- 所有API请求需要进行身份验证
- 基于角色的访问控制
- 敏感操作需要审计日志

## 性能优化

- 使用Redis缓存频繁访问的数据
- 数据库索引优化
- 分页查询
- 异步处理非关键路径

## 日志与监控

- 详细的操作日志
- 业务指标监控
- 异常跟踪

## 扩展点

- 自定义审批规则
- 自定义验证规则
- 事件监听器
- 与其他系统集成的接口

## 测试

模块包含单元测试、集成测试和端到端测试：

```bash
# 运行所有测试
mvn test

# 运行特定模块测试
cd bone-procurement-engine
mvn test
```

## 部署

支持多种部署方式：

1. 独立JAR包部署
2. Docker容器部署
3. Kubernetes集群部署

## 许可证

本项目采用 MIT 许可证 - 详情请参见 LICENSE 文件