# bone-blueprint

参考实现：**展示 Bone DDD 规范的「能力上限」**，并对齐业界 DDD 战术 + 一致性实践（事件发布、CQRS 读侧、ACL、Optional 语义）。

## 战略设计（§13 最小集）

### 限界上下文

| 上下文 | 职责 | 数据所有权 |
|--------|------|------------|
| **订单（Order）** | 订单生命周期：创建、支付、取消 | `t_order`、`t_order_item` |
| **库存（Inventory）** | 库存校验、预留、确认、释放 | 库存服务自有数据（经 ACL 访问） |

### 上下文映射（简图）

```text
[订单上下文] --Customer-Supplier/ACL--> [库存上下文]
       |
       +-- 发布语言：OrderPaidIntegrationEvent (schema 1.0)
```

- 订单**不**直接依赖库存内部模型，仅经 `InventoryGateway`（防腐层）。
- 跨上下文写操作默认**最终一致**：创建预留 → 支付确认 / 取消释放。

### 通用语言（核心术语）

| 中文 | 英文 | 说明 | 禁用同义词 |
|------|------|------|------------|
| 订单 | Order | 聚合根 | Trade、Bill |
| 订单项 | OrderItem | 聚合内实体 | Line、SkuRow |
| 支付 | Pay | 状态 CREATED→PAID | Settle（财务语境） |
| 预留库存 | Reserve Stock | 创建订单后占用 | Lock、Hold（技术词） |
| 确认扣减 | Confirm Stock | 支付后消费预留 | Deduct（单独使用易混淆） |

### 库存协作策略（本样板）

1. **创建订单**：`checkStock` → 持久化 → `reserveStock`（同事务内编排）
2. **支付订单**：聚合 `pay()` → `AFTER_COMMIT` → `confirmStock` + 发布集成事件
3. **取消订单**：聚合 `cancel()` → `AFTER_COMMIT` → `releaseStock`

## 文档

- 权威约定：[doc/architecture/Bone-DDD-最终实践方案.md](../doc/architecture/Bone-DDD-最终实践方案.md)（**第一部分**业界原则 + **第二部分**Bone 落地；**附录 A** 废止旧「Blueprint v×」版本号）  
- 与主工程对齐：[doc/wiki/08-blueprint与主工程对齐.md](../doc/wiki/08-blueprint与主工程对齐.md)  
- 测试说明：[TEST_GUIDE.md](./TEST_GUIDE.md)  
- **§23 极简 / 低成本**：新建业务应优先对齐 **§14.2 + P0（§12.1）**，按需再引入本模块里的演示能力。

## 本模块里「全量演示」包含什么

| 能力 | 用途 |
|------|------|
| **`@Capability` + `HandlerRegistry`（可选）** | 编排侧发现 Handler 元数据；**Adapter 直接调 Handler**，无强制 UseCase 门面，见方案 §20 |
| **领域事件 + AFTER_COMMIT** | 瘦载荷 `record` 事件 + `SpringDomainEventPublisher` + 应用层订阅 |
| **集成事件** | `OrderPaidIntegrationEvent` 与领域事件分离 |
| **扩展点** | 多实现价格计算器（VIP/企业/促销等） |
| **Feign + `InventoryGateway`** | ACL 出站调用 + 预留/确认/释放流程 |
| **CQRS 读侧** | `OrderPageQueryHandler` 使用 `QueryBuilder` |
| **MQ / 定时任务 / RPC** | 入站适配器形态示例 |

无对应需求时，**不必**在新模块中复制上述结构。

## 若要「尽量简单」地抄一版

建议最小子集（示意，类名随域替换）：

1. `domain/{aggregate}/`：聚合根、实体、值对象、领域事件（按需）  
2. `domain/repository/`：写侧仓储接口（继承 SDK `Repository`，不堆查询方法）  
3. `application/command` + `application/query`：命令/查询与 Handler  
4. `application/support/`：`OrderLookup`（Optional 语义）、`AggregatePersistence`（事件发布）  
5. `adapter/web`：Controller、request/response DTO、Assembler  
6. `infrastructure/config` + `infrastructure/event`：元数据、Spring 配置、事件发布实现  

**先不要**：扩展点矩阵、Feign、MQ、Schedule、RPC、`@Capability` 注册表——等业务或集成真的需要再加。

## 构建与测试

```bash
# 首次需安装共享 ArchUnit 规则库
mvn install -pl bone-framework/bone-architecture-test -am -DskipTests

cd bone-blueprint
mvn test
```
