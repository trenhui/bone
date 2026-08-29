# bone-blueprint

参考实现：**展示 Bone DDD 规范的「能力上限」**，并对齐业界 DDD 战术 + 一致性实践（事件发布、CQRS 读侧、ACL、Optional 语义）。

## 战略设计（§13 最小集）

### 限界上下文

| 上下文 | 职责 | 数据所有权 |
|--------|------|------------|
| **订单（Order）** | 订单生命周期：创建、确认支付、取消 | `t_order`、`t_order_item` |
| **支付（Payment）** | 支付单生命周期：发起、渠道预下单、回调确认（幂等） | `bp_payment` |
| **库存（Inventory）** | 库存校验、预留、确认、释放 | 库存服务自有数据（经 ACL 访问） |

### 上下文映射（简图）

```text
[订单上下文] --Customer-Supplier/ACL--> [库存上下文]
       |                                    ^
       | 发起支付(orderId)                    | confirmStock（支付成功回调）
       v                                    |
[支付上下文] --Customer-Supplier/ACL--> [支付渠道(模拟)]
       |
       +-- 领域事件：PaymentSucceededEvent（AFTER_COMMIT 确认订单）
       +-- 发布语言：OrderPaidIntegrationEvent (schema 1.0)
```

- 订单**不**直接依赖库存内部模型，仅经 `InventoryGateway`（防腐层）。
- 支付上下文经 `PaymentGateway`（防腐层）对接渠道；支付成功经领域事件驱动订单确认（跨聚合协作）。
- 跨上下文写操作默认**最终一致**：创建预留 → 支付回调确认 → 库存确认 / 取消释放。

### 通用语言（核心术语）

| 中文 | 英文 | 说明 | 禁用同义词 |
|------|------|------|------------|
| 订单 | Order | 聚合根 | Trade、Bill |
| 订单项 | OrderItem | 聚合内实体 | Line、SkuRow |
| 支付单 | Payment | 支付上下文聚合根，独立于订单，经 orderId 关联 | PayOrder（技术词） |
| 发起支付 | Initiate Payment | 订单 → 生成支付单 → 渠道预下单 → 返回支付链接 | Start Pay |
| 渠道回调 | Payment Callback | 渠道异步通知支付结果（幂等确认） | Notify（易与领域事件混淆） |
| 渠道流水号 | Channel Trade No | 渠道回填的唯一流水号（幂等去重键） | transactionId（易混淆） |
| 预留库存 | Reserve Stock | 创建订单后占用 | Lock、Hold（技术词） |
| 确认扣减 | Confirm Stock | 支付确认后消费预留 | Deduct（单独使用易混淆） |

### 库存与支付协作策略（本样板）

1. **创建订单**：`checkStock` → 持久化 → `reserveStock`（同事务内编排）
2. **发起支付**：加载订单（校验 CREATED）→ 创建 `Payment`（PENDING）→ `PaymentGateway.preCreatePayment` 预下单 → `markPaying` → 返回支付链接
3. **支付回调**：`PaymentSignaturePort` **验签** → `Payment.confirmSuccess`（幂等 + 金额一致性校验）→ 发 `PaymentSucceededEvent` → `AFTER_COMMIT` 确认订单（`confirmPaid`）+ `confirmStock` → 同事务写 **Outbox** 中继 `OrderPaidIntegrationEvent`
4. **查询支付单**：`PaymentReadPort.findById`（读侧直查，CQRS 读模型）
5. **超时关闭**：`CloseExpiredPaymentJob` 定时扫描 PENDING/PAYING 超时单 → `Payment.close()`
6. **退款**：`Payment.refund()`（幂等 + 金额校验）→ 发 `PaymentRefundedEvent` → `AFTER_COMMIT` 确认订单退款（`Order.refund()`）+ `releaseStock`
7. **取消订单**：聚合 `cancel()` → `AFTER_COMMIT` → `releaseStock`
8. **发货 / 送达**：`Order.ship()`（仅 PAID → SHIPPED）/ `Order.deliver()`（仅 SHIPPED → DELIVERED），经 `/api/v1/orders/{id}/ship`、`/ship`、`/deliver` 触发

> **订单状态机**：`CREATED → PAID → SHIPPED → DELIVERED`，分支 `CANCELLED`（CREATED/PAID）、`REFUNDED`（PAID/SHIPPED/DELIVERED 退款）。`ship/deliver/refund` 为领域行为（反贫血 §17），Handler 仅编排调用。

### 支付样板边界说明（业界标准 vs 样板简化）

本模块**演示了**：独立 `Payment` 聚合、反贫血状态机、回调幂等（同流水号重复跳过、异流水号抛错）、金额一致性校验（实付=应付）、**回调验签**（模拟 HMAC，`PaymentSignaturePort` 防腐）、跨聚合经领域事件最终一致、`PaymentGateway` 防腐、退款（幂等 + 金额校验）、超时自动关闭（定时任务）。以下为**真实生产须补齐、样板未内置**的边界，接入前必须按场景实现：

| 项 | 样板现状 | 生产要求 |
|----|----------|----------|
| **并发幂等兜底** | 应用层幂等（聚合内判断） | 须加**乐观锁**（`version` + `UPDATE ... WHERE status='PAYING'`）或 `channel_trade_no` **唯一索引**兜底，防并发回调双写 |
| **回调验签** | 模拟 HMAC（固定共享密钥，`SimulatedPaymentSignatureVerifier`） | 真实渠道用 HMAC/RSA/证书 + 密钥外部化 + 防重放（nonce/时间戳） |
| **真实渠道退款** | `Payment.refund` 仅本地幂等 | 真实退款须调用渠道退款接口 + 对账 |
| **渠道真实对接** | `SimulatedPaymentGatewayImpl` | 替换为真实渠道适配器 + 协议转换 + 错误语义隔离（§19 ACL） |

### 多租户

- 聚合根继承 `TenantAggregateRoot`；写/读路径经 `TenantSupport` / `TenantContext` 隔离。
- HTTP 演示：请求头 `X-Tenant-Id: 1001`（见 `TenantContextFilter`）。

## 文档

- 权威约定：[doc/architecture/Bone-DDD-最终实践方案.md](../doc/architecture/Bone-DDD-最终实践方案.md)（**第一部分**业界原则 + **第二部分**Bone 落地；**附录 A** 废止旧「Blueprint v×」版本号）  
- 与主工程对齐：[doc/wiki/08-blueprint与主工程对齐.md](../doc/wiki/08-blueprint与主工程对齐.md)  
- 测试说明：[TEST_GUIDE.md](./TEST_GUIDE.md)  
- **§23 极简 / 低成本**：新建业务应优先对齐 **§14.2 + P0（§12.1）**，按需再引入本模块里的演示能力。

### Docs-as-Code（合规模板）

| 真源 | 位置 |
|------|------|
| As-Is 能力（CI 派生） | [`doc/_generated/blueprint/`](../doc/_generated/blueprint/) |
| 未落地 Backlog | [`tools/blueprint-compliance-collector/backlog.yaml`](../tools/blueprint-compliance-collector/backlog.yaml) |
| 平台模板 | [Docs-as-Code-模块合规模板](../doc/architecture/Docs-as-Code-模块合规模板.md) |

```bash
bash scripts/ci/collect-blueprint-compliance.sh
```

### REST 契约（OpenAPI）

| 项 | 位置 |
|----|------|
| 契约文件 | [`doc/architecture/openapi/blueprint-orders-v1.yaml`](../doc/architecture/openapi/blueprint-orders-v1.yaml) |
| 前缀 | `/api/v1/orders` |
| 创建语义 | `201 Created` + `Location: /api/v1/orders/{id}` |

## 本模块里「全量演示」包含什么

| 能力 | 用途 |
|------|------|
| **`@Capability` + `HandlerRegistry`（可选）** | 编排侧发现 Handler 元数据；**Adapter 直接调 Handler**，无强制 UseCase 门面，见方案 §20 |
| **领域事件 + AFTER_COMMIT** | 瘦载荷 `record` 事件 + `SpringDomainEventPublisher` + 应用层订阅 |
| **Outbox** | `bp_outbox` + `OrderOutboxWriter` / `OrderOutboxRelay` / `OrderOutboxRelayJob` |
| **集成事件** | `OrderPaidIntegrationEvent` 与领域事件分离，经 Outbox 中继 |
| **多租户** | `TenantAggregateRoot` + `QueryBuilder` 强制 `tenantId` + `X-Tenant-Id` 过滤器 |
| **值对象 Money** | 金额规则集中在 `Money`（`Order` / `OrderItem` 领域计算） |
| **读侧 Join** | `OrderReadPort` + `findOrderWithItems.sql` 扁平投影 → `OrderDetailAssembler` |
| **扩展点** | 多实现价格计算器（VIP/企业/促销等） |
| **独立支付聚合** | `Payment`（`bp_payment`）+ 状态机 + 幂等/金额校验回调（见下） |
| **支付生命周期闭环** | 发起支付 → 渠道预下单 → 回调确认 → **查询**（`PaymentReadPort`）→ **超时关闭**（`CloseExpiredPaymentJob`）→ **退款**（`PaymentRefundedEvent` 驱动订单退款 + 释放库存） |
| **Feign + `InventoryGateway`** | ACL 出站调用 + 预留/确认/释放流程 |
| **CQRS 读侧** | 列表 `QueryBuilder`；详情 SQL 投影 |
| **MQ / 定时任务 / RPC** | 入站适配器形态示例 |
| **真实下单支付场景** | 独立 `Payment` 聚合（`bp_payment`）+ 状态机 + `PaymentGateway` 防腐 + **回调幂等**（`confirmSuccess`）+ 领域事件确认订单（跨聚合协作） |

## 若要「尽量简单」地抄一版

建议最小子集（示意，类名随域替换）：

1. `domain/{aggregate}/`：聚合根、实体、值对象、领域事件（按需）  
2. `domain/repository/`：写侧仓储接口（继承 SDK `Repository`，不堆查询方法）  
3. `application/command` + `application/query`：命令/查询与 Handler  
4. 多租户隔离下沉仓储层（`OrderRepository.findByIdInTenant`，SDK Criteria 查询过滤）；技术横切经 `domain/gateway` 端口 + `infrastructure` 实现（如 `TenantProvider`/`AggregatePersister`），应用层薄 Handler 经端口注入  
5. `adapter/web`：Controller、request/response DTO、Assembler  
6. `infrastructure/config` + `infrastructure/event`：元数据、Spring 配置、事件发布实现  

**需要真实支付时再加**：独立 `Payment` 聚合（`bp_payment`）+ `PaymentGateway` 防腐 + 回调幂等 `confirmSuccess` + `PaymentSucceededEvent` 驱动订单确认（跨聚合协作）——完整链路见本模块「真实下单支付场景」示范。

**先不要**：扩展点矩阵、Feign、MQ、Schedule、RPC、`@Capability` 注册表——等业务或集成真的需要再加。

## RocketMQ Outbox 中继

| 模式 | 配置 | 行为 |
|------|------|------|
| 开发默认 | `bone.blueprint.outbox.mq-enabled=false` | `LoggingOrderMessageSender` 打结构化日志 |
| MQ | `spring.profiles.active=mq` + NameServer | `RocketMqOrderMessageSender` + `OrderPaidIntegrationMqListener` |

> **Outbox 记录归属（工程折中，2026-08-27）**：`OrderOutboxRecord` 作为 `AggregateRoot` 放在 `domain/outbox/`（复用 bone-metadata-sdk 的聚合持久化 + 领域事件机制，避免另起一套基础设施模型）。Outbox 本身是**集成机制**而非订单业务聚合，无领域不变量（仅 PENDING→SENT/FAILED 技术状态）。若未来多模块需要通用 Outbox，应抽到独立基础设施组件，不再占用订单领域包。

```bash
# 启动（需本地 RocketMQ NameServer :9876）
java -jar target/bone-blueprint-1.0.0.jar --spring.profiles.active=mq

# 或环境变量
export BONE_ROCKETMQ_NAMESERVER=localhost:9876
```

支付成功后：同事务写 `bp_outbox` → 定时 `OrderOutboxRelayJob` → Topic `bone.order.paid` → 下游消费。

## 构建与测试

```bash
# 首次需安装共享 ArchUnit 规则库
mvn install -pl bone-framework/bone-architecture-test -am -DskipTests

cd bone-blueprint
mvn test
```
