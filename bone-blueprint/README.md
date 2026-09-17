# bone-blueprint

参考实现：**展示 Bone DDD 规范的「能力上限」**，并对齐业界 DDD 战术 + 一致性实践（事件发布、CQRS 读侧、ACL、Optional 语义）。

## 战略设计（E-1 上下文与数据所有权）

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

1. **创建订单**：`checkStock` → 持久化订单 + 明细 → 发 `OrderCreatedEvent` → **AFTER_COMMIT** 逐条 `reserveStock`（远程调用不占 DB 事务，单条失败仅告警并留痕，见 `OrderCreatedEventHandler`）
2. **发起支付**：加载订单（校验 CREATED）→ 创建 `Payment`（PENDING）→ `PaymentGateway.preCreatePayment` 预下单 → `markPaying` → 返回支付链接
3. **支付回调**：`PaymentSignaturePort` **验签** → `Payment.confirmSuccess`（幂等 + 金额一致性校验）→ 发 `PaymentSucceededEvent` → `AFTER_COMMIT` 确认订单（`confirmPaid`）+ `confirmStock` → 同事务写 **Outbox** 中继 `OrderPaidIntegrationEvent`
4. **查询支付单**：`PaymentQueryPort.findById`（读侧直查，CQRS 读模型）
5. **超时关闭**：`CloseExpiredPaymentJob` 定时扫描 PENDING/PAYING 超时单 → `Payment.close()`
6. **退款**：`Payment.refund()`（**每单仅一次**；重复**同金额**幂等跳过、`false` + warn 留痕，**金额不一致抛错**；退 0 元 / 超付拒绝）→ 发 `PaymentRefundedEvent` → `AFTER_COMMIT` 确认订单退款（`Order.refund()`）+ `releaseStock`
7. **取消订单**：聚合 `cancel()` → `AFTER_COMMIT` → `releaseStock`
8. **发货 / 送达**：`Order.ship()`（仅 PAID → SHIPPED）/ `Order.deliver()`（仅 SHIPPED → DELIVERED），经 `/api/v1/orders/{id}/ship`、`/ship`、`/deliver` 触发

> **订单状态机**：`CREATED → PAID → SHIPPED → DELIVERED`，分支 `CANCELLED`（CREATED/PAID）、`REFUNDED`（PAID/SHIPPED/DELIVERED 退款）。`ship/deliver/refund` 为领域行为（反贫血 E-6.4 / CORE-03），Handler 仅编排调用。

### 支付样板边界说明（业界标准 vs 样板简化）

本模块**演示了**：独立 `Payment` 聚合、反贫血状态机、回调幂等（同流水号重复跳过、异流水号抛错）、金额一致性校验（实付=应付）、**回调验签**（模拟 HMAC，`PaymentSignaturePort` 防腐）、跨聚合经领域事件最终一致、`PaymentGateway` 防腐、退款（幂等 + 金额校验）、超时自动关闭（定时任务）。以下为**真实生产须补齐、样板未内置**的边界，接入前必须按场景实现：

| 项 | 样板现状 | 生产要求 |
|----|----------|----------|
| **并发与一致性护栏** | 并发策略已声明为<strong>乐观锁</strong>（E-5.3 登记）：聚合带 `version` 列，但 Bone 元数据 SDK 通用写路径（`BaseRepository#update` → `DynamicUpdateBuilder`）<strong>不强制</strong> `WHERE version=?`；且其 `TableMetadataResolver` 虽识别 `@Version` 却未被写路径消费（已核验源码）。加之 `domain`/`application` 层按 P0-5 / E-9.3 禁止依赖 `@ReadSideOnly`（即 `Criteria`），无法在应用/领域层合法构造带版本条件的 `updateByCriteria`——故 DB 级乐观锁<strong>待 SDK 启用原生 `@Version` 后落地（当前未生效）</strong> | 当前真实护栏：① 支付回调并发由 `channel_trade_no` **唯一索引**兜底（幂等去重键，防双写）；② 钱货不一致由 `OrderPaymentInconsistencyJob` 周期对账（仅告警、不改单），补偿"支付成功但订单确认丢失"的窗口；③ 订单/支付单状态机防非法跃迁。SDK 乐观锁就绪前，高并发写同一聚合的丢失更新风险仍属<strong>已知登记项</strong> |
| **回调验签** | 模拟 HMAC（固定共享密钥，`SimulatedPaymentSignatureVerifier`） | 真实渠道用 HMAC/RSA/证书 + 密钥外部化 + 防重放（nonce/时间戳） |
| **真实渠道退款** | `Payment.refund` 仅本地幂等 | 真实退款须调用渠道退款接口 + 对账 |
| **渠道真实对接** | `SimulatedPaymentGatewayImpl` | 替换为真实渠道适配器 + 协议转换 + 错误语义隔离（E-4.3 ACL 端口 / P-6） |

### E-6.3 订单明细 PO 分离评估（技术债登记）

`Order` 聚合持有需持久化的 `List<OrderItem>` 集合，且明细有独立表 `t_order_item` 与独立写侧仓储 `OrderItemRepository`（位于 `domain.repository`，与 `OrderRepository` 同包），命中 E-6.3 的 **PO 分离信号**（聚合持有需持久化集合/嵌套实体，无 SDK 级联落库）。

**当前过渡方案**：D1 充血聚合 + `CreateOrderCommandHandler` 显式逐条 `save(OrderItem)`，明细经读侧端口 `OrderQueryPort.findOrderWithItems`（联表投影）读取。`OrderItem` 是 `Order` 聚合内实体，与 `Order` 同事务落库是在保存同一聚合；现有 R9 扫描只能按 Repository/聚合类型提示风险，不能独立证明事务语义。仓储端口统一放 `domain.repository`，**无需也不允许**靠包位置规避门禁（详见 `OrderItemRepository` 类注释）。读端口已按 v5.0 迁至 `application/query/port`（`OrderQueryPort` / `PaymentQueryPort`），配套行 DTO 置于 `application/query/dto`；原 `domain/gateway/*ReadPort` 与 `domain/{order,payment}/read` 已清理，无遗留存量。

**与 CORE-11 的偏差（显式登记）**：CORE-11 要求「聚合根是唯一持久化入口，子实体随根落盘，不为子实体建立独立聚合级 Repository」。本模块的 `OrderItemRepository` 与该条字面要求不符，属 **SDK 能力缺失导致的被迫偏差**，而非风格选择：Bone 元数据 SDK **不支持聚合级联落库**，`OrderRepository.save(order)` 不会持久化 `order.items`（且 `items` 标 `@Transient` 以避免 SDK 误映射为 `t_order` 列）。若无显式明细写入路径，订单明细将**静默丢失**。因此「显式逐条 `save(OrderItem)`」是 SDK 约束下的最小可行路径：`OrderItem` 仍是 `Order` 聚合内实体（**未**升格为聚合根），两次 save 在**同一事务**内完成，一致性边界仍等于 `Order` 聚合——CORE-11 的保护目标未被削弱，只是落库入口由「仅根」变为「根 + 子实体同事务双写」。**收敛路径**：SDK 支持聚合级联后即可删除 `OrderItemRepository`、明细随根落盘，恢复 CORE-11 完整合规（与下方 E-6.3 迁移条件同源）。

**迁移条件（E-6.3 路径）**：当 SDK 支持聚合级联，或团队决定消除 D1 注解与「显式逐条 save」的折中时，再按 E-6.3 做 PO 分离——建 `OrderItemPO` + `OrderItemConverter`，领域 `OrderItem` 落回 D0，仓储实现改操作 PO。此处为已知点，当下合规、不影响功能。

### E-7.1 强类型 ID 过渡态登记（存量记录）

`Order.customerId`、`Payment.orderId` / `customerId` 当前为裸 `Long`。依据 E-7.1 **存量记录**条款（遗留自增主键按模块登记），当下合规。

- **新模块指引**：**新增强聚合**的跨聚合引用必须使用强类型 ID 值对象（`record OrderId(Long value)` 等，P-3.2 口径：构造期空值校验、无 setter），禁止裸 `Long`——编译期即可拦截「订单 ID / 客户 ID 写反」类静默数据错乱。
- **本样板改造触发条件**：与 E-6.3 PO 分离联动——强类型 ID 的持久化转换依赖 Converter（E-6.3 的 PO/Converter 分离），而本模块聚合直接落库（无 PO 分层）。待 PO 分离落地后，随 `OrderItemPO` 一并引入 `OrderId` / `CustomerId`，避免二次返工。

### E-10 domain 分组形态登记

本模块 `domain/` 采用 **`domain/{aggregate}` 按聚合平铺**形态，**不采用** `domain/model/{aggregate|entity|valueobject|event}` 按构件角色分组的形态；同一 `domain` 包内**不得混用**两套分组标准。实际子包：

| 子包 | 内容 |
|---|---|
| `domain/order`、`domain/payment` | 聚合根（`Order` / `Payment`）与聚合内实体（`OrderItem`） |
| `{aggregate}/event` | 上下文内领域事件（`OrderPaidEvent` / `PaymentSucceededEvent` 等，过去式） |
| `{aggregate}/valueobject` | 聚合内值对象（`OrderStatus` / `PaymentStatus` / `PaymentChannel` 等） |
| `domain/shared/valueobject` | 跨聚合共享值对象（`Money`） |
| `domain/repository` | 写侧聚合仓储接口（`OrderRepository` / `PaymentRepository` / `OrderItemRepository`） |
| `domain/gateway` | 外部**业务**能力端口（`InventoryGateway` / `PaymentGateway`），按 E-4.3 只放业务事实 |
| `domain/extension/order` | 定价策略业务端口（`OrderPriceCalculator`）；扩展引擎技术契约下沉到 `infrastructure/extension/order`，domain 不感知框架 |

E-10 明确该平铺形态为**合法变体而非存量债务**，但要求「选择后在模块 README 登记」——本节即本模块的登记点，供后续评审与 `studio-generator` 生成目标对齐。

**与 E-10 参考结构的另一处偏差（登记）**：参考结构给出 `infrastructure/persistence/OrderRepositoryImpl`（= `domain/repository` 的实现），本模块**不存在 `infrastructure/persistence` 包**——仓储由 Bone 元数据 SDK 的 `@EnableSqlRepositories` **运行时生成代理实现**，没有可手写的实现类。故写侧无 `*Impl`/`*PO` 落点，与 E-6.3 登记的 PO 分离缺口同源。

### 多租户

- 聚合根继承 `TenantAggregateRoot`；写/读路径经 `TenantProviderAdapter`（实现 `application/port/out/TenantProvider` 端口）/ `TenantContext` 隔离。
- **租户来源由 token 决定，不由请求头决定**：请求带 token 时，框架 `AbstractJwtAuthenticationFilter` 会把 `X-Tenant-Id` 的**读取值**改写为 token 内<strong>已签名</strong>的 `tenantId` claim，调用方改这个请求头无效（伪造只会被纠正并留 WARN 日志）。只有**无 token 的内部调用**才由调用方提供该头。租户上下文统一由 `bone-web` 的 `TenantInterceptor`（本模块在 `WebMvcConfiguration` 注册）建立——框架内不再另置租户 filter，避免同一规则两处实现、且其中一处读到未归一化的头。
- **两处强制校正、互为兜底**：① 经网关时 `bone-gateway` 的 `JwtAuthGlobalFilter` 验签后用 claim **覆盖** `X-Tenant-Id`（并注入 `X-User-Id` / `X-Roles`）；② 直连模块端口时框架过滤器做同样的归一化。因此该头是**内部信任头**：`bone-web` 的 `TenantInterceptor` 与 `bone-metadata-sdk` 的租户数据源路由无论先后都读得到真值。

### 鉴权（JWT，与平台同范式）

- 本模块**不签发 token，也不调用 IAM**：IAM 用共享密钥 `bone.iam.jwt.secret-key` 签发，各模块用框架 `JwtTokenService` **本地离线验签**。若改成每次请求回调 IAM，就把 IAM 变成了所有服务的可用性单点。
- `infrastructure/config/security/SecurityConfig` + `infrastructure/security/JwtAuthenticationFilter`（继承框架 `AbstractJwtAuthenticationFilter`，15 行），与 `bone-system` / `bone-integration` / `bone-metadata-server` 等模块写法一致。**没有 `SecurityFilterChain` 会落到 Spring Boot 默认策略（HTTP Basic + 表单登录 + 启动随机密码），接口一律 401**。
- 所有 `/api/**` 需 `Authorization: Bearer <token>`；`/actuator/**`、`/swagger-ui/**`、`/v3/api-docs/**` 与 CORS 预检放行。
- **密钥必须与 IAM 一致**，否则会出现「token 明明有效却 401」的假象（日志有 `[JWT] Token 解析失败`）。IAM 的 dev 默认密钥是 `dev-only-secret-key-minimum-32-bytes-long`（可被 `BONE_IAM_JWT_SECRET_KEY` / `BONE_JWT_SECRET` 覆盖）；本模块不配置时会退回 `JwtConfig` 的内置默认值，**两者不同**，必须显式对齐：

```bash
# 1) 向 IAM 取 token
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/iam/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' | jq -r '.data.token')

# 2) 用同一个密钥启动本模块（也可设环境变量 BONE_IAM_JWT_SECRET_KEY）
mvn spring-boot:run \
  -Dspring-boot.run.arguments="--bone.iam.jwt.secret-key=dev-only-secret-key-minimum-32-bytes-long"

# 3) 访问（租户取自 token 的 tenantId claim；IAM 默认 admin 属平台租户 0）
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8082/api/v1/orders
```

> 再额外带上 `X-Tenant-Id: 1001` 也不会改变结果——带 token 时框架按 claim 覆盖该头。要演示别的租户，得换一个 `tenantId` claim 对应的 token，而不是改请求头。

- **生产必须设置** `BONE_IAM_JWT_SECRET_KEY`（≥ 32 字节）：仍用默认密钥时 `JwtConfig` 在 `prod` profile 下**拒绝启动**。
- **JJWT 版本不能降级**：本模块固定 `${jjwt.version}`（0.12.x），与 `bone-security` 的编译版本一致。若被传递依赖降到 0.11.x，`JwtTokenService.parse()` 会抛 `NoSuchMethodError`，症状同样是「带了有效 token 仍 401/500」。

#### 平台租户打洞登记（E-2 多租户）

定时任务线程无请求上下文，经 `TenantProvider` 端口取数时由 `TenantProviderAdapter` **显式降级为平台租户并留审计日志**（禁止静默按 `null` 放行）。以下异步入口已按 E-2 多租户登记：

| 异步入口 | 作用租户 | 降级/打洞说明 |
|----------|----------|--------------|
| `CancelExpiredOrderJob` | 平台租户 `0`（上下文缺失时） | 定时扫描超时订单；已在任务内显式打印 `[打洞]` 日志 |
| `CloseExpiredPaymentJob` | 平台租户 `0`（上下文缺失时） | 定时关闭超时支付单；已在任务内显式打印 `[打洞]` 日志 |
| `OrderOutboxRelayJob` | 随事务内租户（Outbox 记录自带 `tenant_id`） | 逐条中继按记录租户发送，不依赖线程上下文 |

### 写侧标准写法（保存 + 发布事件）

```java
order.cancel();
orderRepository.save(order);
domainEventPublisher.publishFrom(order); // default 方法：publishAll + clearDomainEvents
```

- `publishFrom` 是 `DomainEventPublisher` 的 default 方法，等价于业界 Spring Data `@DomainEvents` 的显式写法。
- **不经过任何持久化端口**：原 `AggregatePersister`（需传 `Repository` 入参）已删除——属 Service Locator 反模式，且 `save/update` 两方法实现重复。
- 不发事件的写操作（如 `Order.ship()`）直接 `repository.save(order)` 即可，无需 `publishFrom`。

### 事件订阅器 `@Transactional` 规则（P1-1）

| 订阅器内是否写库 | 是否加 `@Transactional` | 示例 |
|------------------|------------------------|------|
| **是**（改聚合并保存） | **必须加**（AFTER_COMMIT 后开启新事务） | `PaymentSucceededEventHandler`、`PaymentRefundedEventHandler` |
| 否（只读 + 远程调用） | 不加 | `OrderPaidEventHandler`、`OrderCancelledEventHandler` |

> 领域事件经 `SpringDomainEventPublisher` 同步投递到 Spring 事件总线；`@TransactionalEventListener(AFTER_COMMIT)` 保证**事务提交后才触发**，事务回滚则不会执行。

## 文档

- 权威约定：[doc/architecture/Bone-DDD-最终实践方案.md](../doc/architecture/Bone-DDD-最终实践方案.md)（原则、Bone 工程决策与门禁口径）
- 与主工程对齐：[doc/wiki/08-blueprint与主工程对齐.md](../doc/wiki/08-blueprint与主工程对齐.md)  
- 测试说明：[TEST_GUIDE.md](./TEST_GUIDE.md)  
- **新模块最小路径**：先定义上下文与数据所有权，再选一个应用用例边界。默认 `Controller → ApplicationService → Repository`（读写同一入口：读直查写仓储）；仅当**读模型与聚合分歧**（报表/多表组合）才在 ApplicationService 内引入 `QueryPort`，仅当**命令异步/多入口/需独立路由**才加 `Command(Handler)`。**不要预建 `command/`、`query/handler/` 空目录**（E-3.7 / AS-01～AS-04 / ADR-0028）。

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
| **`@Capability` + `HandlerRegistry`（可选）** | 编排侧发现 Handler 元数据；**Adapter 直接调 Handler**，无强制 UseCase 门面，见 E-3 应用用例（E-3.7 入口构件决策 / ADR-0028） |
| **领域事件 + AFTER_COMMIT** | 瘦载荷 `record` 事件 + `SpringDomainEventPublisher` + 应用层订阅 |
| **Outbox** | `bp_outbox` + `OrderOutboxWriter` / `OrderOutboxRelay` / `OrderOutboxRelayJob` |
| **集成事件** | `OrderPaidIntegrationEvent` 与领域事件分离，经 Outbox 中继 |
| **多租户** | `TenantAggregateRoot` + 写侧 `findByIdInTenant`（`QueryParam` 条件，租户缺失即失败关闭）/ 读侧 SQL 显式 `tenant_id = :tenantId` + `X-Tenant-Id` 过滤器（生产由网关按 token claim 覆盖下发） |
| **JWT 鉴权** | `SecurityConfig` + `JwtAuthenticationFilter`（框架 `AbstractJwtAuthenticationFilter`）；共享密钥离线验签，**不回调 IAM** |
| **值对象 Money** | 金额规则集中在 `Money`（位于 `domain/shared/valueobject`，`Order` / `OrderItem` / **`Payment`** 共用——共享值对象独立成包，避免支付反向依赖订单包） |
| **读侧 Join** | `OrderQueryPort` + `findOrderWithItems.sql` 扁平投影 → `OrderDetailAssembler` |
| **扩展点** | 多实现价格计算器（VIP/企业/促销等） |
| **独立支付聚合** | `Payment`（`bp_payment`）+ 状态机 + 幂等/金额校验回调（见下） |
| **支付生命周期闭环** | 发起支付 → 渠道预下单 → 回调确认 → **查询**（`PaymentQueryPort`）→ **超时关闭**（`CloseExpiredPaymentJob`）→ **退款**（`PaymentRefundedEvent` 驱动订单退款 + 释放库存） |
| **Feign + `InventoryGateway`** | ACL 出站调用 + 预留/确认/释放流程 |
| **CQRS 读侧** | 列表 / 详情均经读侧端口 SQL 投影（`OrderQueryPort` / `PaymentQueryPort`），写侧仓储不承载报表查询 |
| **MQ / 定时任务 / RPC** | 入站适配器形态示例（MQ 消费端幂等落库、DLQ、消费指标见上节） |
| **幂等写（`Idempotency-Key`）** | `BlueprintIdempotencyService` + `IdempotencyStore`（落 `bp_idempotency_record`）+ 控制器取头；同键同 body 重放同一响应、同键异 body → 409 `COMMON_IDEMPOTENCY_CONFLICT`、TTL 24h（API 规范 §6.1/§8） |
| **授权（Scope）** | 端点声明 `@PreAuthorize("hasAuthority('order:orders:read'/'order:orders:write')")`；scope 由 IAM 随 token 下发（API 规范 §9.2） |
| **稳定错误码** | `common/BlueprintErrorCodes`（`BP_*`，登记于错误码登记 §6）；抛出统一用 `new BizException(HTTP 状态, 码 + ": " + 说明)`——`BizException(String)` 默认码是 **500**，会把 404/400 报成服务端故障。**翻译成 HTTP 状态 + 错误信封由 bone-web 的 `BoneWebExceptionAutoConfiguration` 提供**（该处理器此前没有任何注册入口，是死代码 → 业务异常直落 servlet 容器变 500；已在框架侧补 auto-configuration，模块无需扫描/导入） |
| **日志与链路** | `BoneRequestContextFilter`（MDC `traceId`/`tenantId`/`userId`/`httpRoute` + 每请求一条 `[API]` INFO + 回显 `X-Request-Id`）；身份在认证过滤器写入（安全链结束会清空 `SecurityContextHolder`）。**类名带 `Bone` 前缀是必需的**：Spring Boot 自动配置已注册名为 `requestContextFilter` 的 Bean，同名会启动即失败 |
| **DDL 真源** | 表结构只在仓库根 `bone-init.sql`；模块内无建表脚本且 `spring.sql.init.mode: never`，避免双轨 DDL 漂移 |
| **真实下单支付场景** | 独立 `Payment` 聚合（`bp_payment`）+ 状态机 + `PaymentGateway` 防腐 + **回调幂等**（`confirmSuccess`）+ 领域事件确认订单（跨聚合协作） |

## 若要「尽量简单」地抄一版

建议最小子集（示意，类名随域替换）：

1. `domain/{aggregate}/`：聚合根、实体、值对象、领域事件（按需）  
2. `domain/repository/`：写侧仓储接口（继承 SDK `Repository`，不堆查询方法）  
3. `application/`：语义化 ApplicationService（默认入口，写方法 + 读方法）；写意图需显式契约时才加 `command/cmd` + `command/handler`，读模型分歧时才加 `query/port` + `query/dto`  
4. 多租户隔离下沉仓储层（`OrderRepository.findByIdInTenant`，bone-core `QueryParam` + `Operator` 条件查询过滤，租户缺失即失败关闭—读侧 DSL 不得进 domain）；技术横切经 `application/port/out` 端口 + `infrastructure` 实现（如 `TenantProvider` → `TenantProviderAdapter`），应用层薄 Handler 经端口注入  
5. **写侧标准写法**：`repository.save(aggregate)` + `domainEventPublisher.publishFrom(aggregate)`（不设持久化端口）  
6. `adapter/web`：Controller、request/response DTO、Assembler  
7. `infrastructure/config` + `infrastructure/event`：元数据、Spring 配置、事件发布实现  

**需要真实支付时再加**：独立 `Payment` 聚合（`bp_payment`）+ `PaymentGateway` 防腐 + 回调幂等 `confirmSuccess` + `PaymentSucceededEvent` 驱动订单确认（跨聚合协作）——完整链路见本模块「真实下单支付场景」示范。

**先不要**：扩展点矩阵、Feign、MQ、Schedule、RPC、`@Capability` 注册表——等业务或集成真的需要再加。

## RocketMQ Outbox 中继

| 模式 | 配置 | 行为 |
|------|------|------|
| 开发默认 | `bone.blueprint.outbox.mq-enabled=false` | `LoggingOrderMessageSender` 打结构化日志 |
| MQ | `spring.profiles.active=mq` + NameServer | `RocketMqOrderMessageSender` + `OrderPaidIntegrationMqListener` |

> **Outbox 记录归属（工程折中，2026-08-27）**：`OrderOutboxRecord` 位于 `infrastructure/messaging/outbox/`（原 `domain/outbox/` 包已废弃）。它**不是业务聚合**，仅因复用 bone-metadata-sdk 的聚合持久化与主键回填机制而继承 `AggregateRoot`；无领域不变量（仅 PENDING→SENT/FAILED 技术状态）。若未来多模块需要通用 Outbox，应抽到独立基础设施组件，不再占用订单领域包。

### 可靠投递的三件套（缺一不可）

| 环节 | 实现 | 规范依据 |
|------|------|----------|
| **生产端原子** | `OrderOutboxWriter`（`Propagation.MANDATORY`，强制与业务写同事务）+ 信封含 `eventId`/`eventType`/`topic`/`occurredAt`/`tenantId`/`traceId`/`schemaVersion` | 消息与事件规范 §3/§7 |
| **中继** | `OrderOutboxRelay`：PENDING → SENT；失败累计重试，**超限转投 `platform.dead_letter.v1`**（信封内已带 `topic`，重放无需回查表）；指标 `bone_mq_send_total{topic,status}` | §6/§9 |
| **消费端幂等** | `ConsumedEventPort` → `bp_processed_event`（`(consumer_group, event_id)` 唯一键**原子抢占**，非「先查后写」）；抢占与业务动作在**同一事务**（`OrderPaidConsumptionApplicationService`）——否则处理失败重试会被自己的幂等记录挡住而丢事件；指标 `bone_mq_consume_total{topic,status}` | §5/§9、ADR-0021 |

> Topic 命名遵循 §2（`{scope}.{domain}.{resource}_{action}.v{major}`），5 个集成事件已登记 §4 事件注册表。

### 幂等写与 `@Capability` 的两处口径（供其他模块照抄前先读）

**① 幂等写**：机制在应用层（`application/service/BlueprintIdempotencyService`：作用域键 `租户|用户|键|方法|路径`、SHA-256
载荷指纹、24h TTL、冲突判定），入口在 adapter（`OrderController.create` 先问「该键是否已有响应」）。**为什么不把
`ResponseEntity` 交给应用 Handler**：那样会让 HTTP 类型穿透应用层（E-10.1）；平台既有 `StudioIdempotencyService` /
`CatalogIdempotencyService` 是在 Handler 内调用并返回 `ResponseEntity` 的存量形态，本模块是其分层更干净的版本。
存储用 MySQL（`bp_idempotency_record`）而非平台常见 Redis，因为本模块无 Redis 依赖，语义等价。

**② `@Capability` 只能标在类上**（`@Target(TYPE)`）：所以它天然适配「一个类一个用例」的 `*CommandHandler`，而
`OrderApplicationService` 这类**承载多个用例的应用服务没法逐个用例标注**——给它加一个类级注解反而会把「多用例服务」
误报成单一能力边界。因此本模块只有 CommandHandler 带 `@Capability`；若希望 ApplicationService 也能被编排/AI 发现，
需要框架把注解开放到方法级（待办项，非本模块可解）。

### 授权（Scope）

| 端点 | 所需 scope |
|------|-----------|
| `GET /api/v1/orders`、`GET /api/v1/orders/{id}` | `order:orders:read` |
| `POST /api/v1/orders`、`POST /api/v1/orders/{id}/{cancel,ship,deliver}` | `order:orders:write` |

scope 由 IAM 登录时按「账号 → 角色（含继承闭包）→ 权限」解析并写入 JWT 的 `scopes` claim，框架把它映射为 authority，
`@PreAuthorize("hasAuthority(...)")` 据此判定（认证失败 401 / 无权限 403，见 `SecurityConfig` 的 `exceptionHandling`）。
权限目录种子在 `bone-init.sql`（`iam_permission` + `iam_role_permission`，管理员角色已授予），管理员另有代码侧回退清单
`DefaultPermissionCodes#adminFallback`——**本地不必先造角色即可跑通**。照抄本模块时把 scope 换成自己域的命名。

**已知未覆盖（登记，非遗漏）**：① 支付端点（`PaymentController`）尚未声明 scope——该文件正随验签端口迁移一起改动，
避免同批冲突；② 渠道回调 `POST /api/v1/payments/callback` 目前仍要求认证，而真实渠道无法持有 JWT，生产需改为
「白名单放行 + 验签即认证」或由网关代签内部凭证——与「验签迁到应用层」是同一次改造；③ **本模块仍无装配级测试**
（无 `@SpringBootTest` / MockMvc 契约 / Testcontainers 集成测试，`pom.xml` 缺测试依赖）。这不是形式主义：本轮实机验证抓到的
4 个缺陷中有 3 个（Bean 名与框架冲突导致起不来、统一异常处理器未注册导致错误全变 500、SDK 读路径缺 `Instant` 转换导致读库
500）**单测全绿也照样存在**——收敛路径是先补「上下文加载 + 一条 HTTP 主链路」，再谈覆盖率阈值。

> **持久化模型的时间字段一律用 `Instant`**（UTC 语义，与领域事件一致），这依赖 bone-metadata-sdk 读路径的
> `InstantConverter`——它此前缺失，表现为「写得进、读不出」（非空 DATETIME 列读取抛
> `UnsupportedConversionException: LocalDateTime → Instant`）。该转换器已补入 SDK，时区口径与写入路径
> （`ReservedColumnsHandler` 的 `atZone(systemDefault)`）对称，往返无损。

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
