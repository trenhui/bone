# bone-blueprint

参考实现：**展示 Bone DDD 规范的「能力上限」**，并对齐业界 DDD 战术 + 一致性实践（事件发布、CQRS 读侧、ACL、Optional 语义）。

## 战略设计（E-1 上下文与数据所有权）

### 限界上下文

| 上下文 | 职责 | 数据所有权 |
|--------|------|------------|
| **订单（Order）** | 订单生命周期：创建、确认支付、取消 | `t_order`、`t_order_item` |
| **支付（Payment）** | 支付单生命周期：发起、渠道预下单、回调确认（幂等） | `bp_payment` |
| **库存（Inventory）** | 库存校验、预留、确认、释放 | 库存服务自有数据（经 ACL 访问） |

### 本上下文拥有的表（E-1.2 数据所有权声明）

**这是机器可读声明**：`SqlTemplateGovernanceTest` 的门禁⑥ 会解析本表，校验本模块自定义 SQL 的 `FROM`/`JOIN`
表集合必须 ⊆ 对应聚合名下的表——未登记即判违规（跨聚合读应走 `application/query/port` 的 `*QueryPort`）。

| 表名 | 用途 | 归属聚合 |
|------|------|----------|
| `t_order` | 订单头（含 `tenant_id`、审计与软删字段） | Order |
| `t_order_item` | 订单明细，聚合内实体；无独立租户列，租户隔离经 `t_order.tenant_id` 间接保证 | Order |
| `bp_payment` | 支付单（独立聚合，经 `orderId` 关联订单） | Payment |
| `bp_outbox` | Outbox 投递记录（可靠投递三件套之一） | —（基础设施表，非聚合） |
| `bp_idempotency_record` | 幂等请求记录 | —（基础设施表，非聚合） |
| `bp_processed_event` | 消费去重记录 | —（基础设施表，非聚合） |


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
4. **查询支付单**：`PaymentApplicationService.getById`（经 `PaymentRepository` + `PaymentDetailAssembler`）；全租户运维扫描同样落在 `PaymentRepository` 的 `*AllTenants` 方法上——见下方「读侧归属规则」
5. **超时关闭**：`CloseExpiredPaymentJob` 定时扫描 PENDING/PAYING 超时单 → `Payment.close()`
6. **退款**：`Payment.refund()`（**每单仅一次**；重复**同金额**幂等跳过、`false` + warn 留痕，**金额不一致抛错**；退 0 元 / 超付拒绝）→ 发 `PaymentRefundedEvent` → `AFTER_COMMIT` 确认订单退款（`Order.refund()`）+ `releaseStock`
7. **取消订单**：聚合 `cancel()` → `AFTER_COMMIT` → `releaseStock`
8. **发货 / 送达**：`Order.ship()`（仅 PAID → SHIPPED）/ `Order.deliver()`（仅 SHIPPED → DELIVERED），经 `/api/v1/orders/{id}/ship`、`/ship`、`/deliver` 触发

> **订单状态机**：`CREATED → PAID → SHIPPED → DELIVERED`，分支 `CANCELLED`（CREATED/PAID）、`REFUNDED`（PAID/SHIPPED/DELIVERED 退款）。`ship/deliver/refund` 为领域行为（反贫血 E-6.4 / CORE-03），Handler 仅编排调用。

### 支付样板边界说明（业界标准 vs 样板简化）

本模块**演示了**：独立 `Payment` 聚合、反贫血状态机、回调幂等（同流水号重复跳过、异流水号抛错）、金额一致性校验（实付=应付）、**回调验签**（模拟 HMAC，`PaymentSignaturePort` 防腐）、跨聚合经领域事件最终一致、`PaymentGateway` 防腐、退款（幂等 + 金额校验）、超时自动关闭（定时任务）。以下为**真实生产须补齐、样板未内置**的边界，接入前必须按场景实现：

| 项 | 样板现状 | 生产要求 |
|----|----------|----------|
| **并发与一致性护栏** | 并发策略已声明为<strong>乐观锁</strong>（E-5.3 登记）并由 SDK 原生 `@Version` 提供（**D2 已落地**）：`Order`/`Payment` 标注 `@Version`，写路径 `update` 自动 `SET version=version+1` + `WHERE version=:old`，冲突抛 `OptimisticLockingFailureException` 并在应用层翻译为 `OptimisticLockConflictException` | 真实护栏仍然需要：① 支付回调并发由 `channel_trade_no` **唯一索引**兜底（幂等去重键，防双写）；② 钱货不一致由 `OrderPaymentInconsistencyJob` 周期对账（仅告警、不改单）；③ 订单/支付单状态机防非法跃迁。SDK 乐观锁已就绪，高并发写同一聚合的丢失更新由版本冲突拦截，但仍建议结合唯一约束防御 |
| **回调验签** | 模拟 HMAC（固定共享密钥，`MockPaymentSignaturePortAdapter`） | 真实渠道用 HMAC/RSA/证书 + 密钥外部化 + 防重放（nonce/时间戳） |
| **真实渠道退款** | `Payment.refund` 仅本地幂等 | 真实退款须调用渠道退款接口 + 对账 |
| **渠道真实对接** | `MockPaymentGatewayAdapter` | 替换为真实渠道适配器 + 协议转换 + 错误语义隔离（E-4.3 ACL 端口 / P-6） |

### E-6.3 订单明细与模型形态登记

`Order` 聚合持有需持久化的 `List<OrderItem>`（独立表 `t_order_item`）。写侧已走 SDK `@Cascade`：`items` 同时标 `@Transient` 与 `@Cascade(foreignKey = "orderId")`，`OrderRepository.save` 在同一事务内落明细。`OrderItemRepository` 已删除，应用层不再逐条 `save(OrderItem)`。

**读侧**：`findById` 不回填集合（**明确不做**级联读回填）。明细经域仓储读方法 `OrderRepository.findOrderWithItems`（`@Sql` 联表投影）读取。**ADR-0030 合并形态**：订单读侧不再单设 `OrderQueryPort` / `OrderReadRepository`，本聚合读方法（`@Sql` + Criteria）与域层投影（`domain/model/order/projection/`）并入 `OrderRepository`；支付侧亦已按 ADR-0030 §P4 同模式折叠。

**与 CORE-11**：聚合根是唯一写入口，`OrderItem` 未升格为聚合根。

**明确不做**：强类型 ID 替换裸 `Long`；为强类型 ID 做 `OrderItemPO` + Converter / PO 分离；级联读回填与批量级联。依据见 [Bone-Metadata-SDK-能力需求.md](../doc/architecture/Bone-Metadata-SDK-能力需求.md) 与主规约 E-4.1 / E-6.3 / E-7.1。

### E-7.1 强类型 ID（存量记录 · 明确不做迁移）

`Order.customerId`、`Payment.orderId` / `customerId` 当前为裸 `Long`。依据 E-7.1 **存量记录**条款，当下合规；**本样板不再推进**强类型 ID 或 PO 分离。

- **新模块指引**：若模块选 Shared / 无 Converter，继续用 `Long` 并在 README 登记；不要为「理想强类型」强行拆 PO。
- **domain 持久化耦合范围（SDK 偏差边界）**：新增聚合若需持久化，仅允许在**聚合根/实体类**上使用 SDK 的 `@Table` / `@Version` / `@Transient` / `@Cascade` / `@Id` / `@GeneratedValue` 等元数据注解。**不要**为普通值对象或领域服务引入持久化注解，**不要**在 `domain` 包内（`domain.repository` 除外）新增查询 DSL。
- **本样板改造触发条件**：无。曾写的「随 PO 分离引入 OrderId」路径已取消。

### E-10 domain 分组形态登记

**目标形态**（[ADR-0036](../../doc/architecture/adr/0036-domain-model-package-single-standard.md)，2026-09-22 起为平台唯一形态）：聚合构件置于 `domain/model/{聚合}/`，聚合根 / 聚合内实体 / 值对象在聚合包内**直接平铺**，`event/` `projection/` `valueobject/` 为聚合内子包；`repository` / `gateway` / `extension` 端口留在 `domain/` 根。

**本模块现状**：**已迁移至目标形态**（2026-09-22 完成，是 ADR-0036 的首个落地模块）。聚合构件全部位于 `domain/model/{order,payment,shared}/`，端口包留在 `domain/` 根。当前子包：

| 子包 | 内容 |
|---|---|
| `domain/model/order`、`domain/model/payment` | 聚合根（`Order` / `Payment`）与聚合内实体（`OrderItem`） |
| `domain/model/{aggregate}/event` | 上下文内领域事件（`OrderPaidEvent` / `PaymentSucceededEvent` 等，过去式） |
| `domain/model/{aggregate}/valueobject` | 聚合内值对象（`OrderStatus` / `PaymentStatus` / `PaymentChannel` 等） |
| `domain/model/{aggregate}/projection` | 读侧投影（`OrderHeadProjection` / `OrderWithItemsProjection` / `PaymentProjection`） |
| `domain/model/shared/valueobject` | 跨聚合共享值对象（`Money`） |
| `domain/model/shared/exception` | 跨聚合共享领域异常（`OptimisticLockConflictException` / `StateConflictException`） |
| `domain/repository` | 聚合仓储接口（`OrderRepository` / `PaymentRepository`）——ADR-0030 起**写侧 + 本聚合读**同处一个接口；明细随根 `@Cascade` 落盘，不再单设子实体仓储；全租户运维入口以 `*AllTenants` 后缀声明 |
| `domain/gateway` | 外部**业务**能力端口（`InventoryGateway` / `PaymentGateway`），按 E-4.3 只放业务事实；出站实现在 `infrastructure/gateway/{外部系统}` |
| `domain/extension/order` | 定价策略业务端口（`OrderPriceCalculator`）与其入参模型（`OrderPriceRequest` record）；扩展引擎技术契约下沉到 `infrastructure/extension/order`，domain 不感知框架 |

**已完成的迁移（ADR-0036 D1）**：`domain/{order,payment}` → `domain/model/{order,payment}`（`event` / `valueobject` / `projection` 子包名已合规，原样下沉）；`domain/shared/{valueobject,exception}` → `domain/model/shared/…`；`domain/{repository,gateway,extension}` **不变**（端口不进 `model/`）。本次同步更新了 15 处引用侧 import 与 3 个测试类的包路径；212 个测试全绿。

> 本节是「目标形态 + 本模块迁移状态」的登记点（原「平铺属合法变体」的口径已由 ADR-0036 取代）。启动类的 `@EnableExtensionPoints(basePackages = "com.bone.blueprint.domain.extension")` 是**字符串包名**，IDE 重命名不会改它——因 `extension` 按 ADR-0036 R3 留根，本次已逐处核对确认无需改动；后续若移动 `extension/` 必须同步改该字面量。

**与 E-10 参考结构的另一处偏差（登记）**：参考结构给出 `infrastructure/persistence/OrderRepositoryImpl`（= `domain/repository` 的实现），本模块**不存在 `infrastructure/persistence` 包**——仓储由 Bone 元数据 SDK 的 `@EnableSqlRepositories` **运行时生成代理实现**，没有可手写的实现类。故写侧无 `*Impl`/`*PO` 落点；与「明确不做 PO 分离」一致，不是待还技术债。

### 多租户

- 聚合根继承 `TenantAggregateRoot`；写/读路径经 `TenantPortAdapter`（实现 `application/port/out/TenantPort` 端口）/ `TenantContext` 隔离。
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

定时任务线程无请求上下文，经 `TenantPort` 端口取数时由 `TenantPortAdapter` **显式降级为平台租户并留审计日志**（禁止静默按 `null` 放行）。以下异步入口已按 E-2 多租户登记：

| 异步入口 | 作用租户 | 降级/打洞说明 |
|----------|----------|--------------|
| `CancelExpiredOrderJob` | 平台租户 `0`（上下文缺失时） | 定时扫描超时订单；扫描结果以 `[全租户扫描]` 前缀留痕（`findExpiredUnpaidOrdersAllTenants`） |
| `CloseExpiredPaymentJob` | 平台租户 `0`（上下文缺失时） | 定时关闭超时支付单；扫描结果以 `[全租户扫描]` 前缀留痕（`findExpiredOpenPaymentsAllTenants`） |
| `OrderPaymentInconsistencyJob` | 全部租户（逐行取该行 `tenantId` 查订单状态） | 「钱货不一致」对账；以 `[全租户对账]` 前缀留痕（`findSettledPaymentsCreatedBeforeAllTenants`），检出的偏差同事务落 Outbox |
| `OrderOutboxRelayJob` | 随事务内租户（Outbox 记录自带 `tenant_id`） | 逐条中继按记录租户发送，不依赖线程上下文 |

上述三个扫描任务的**周期与门限均可配置**（`bone.blueprint.schedule.*`）：cron 由 `@Scheduled` 占位符直读，超时阈值 /
宽限期由 `@Value` 注入（默认订单 30min、支付 30min、对账宽限 10min）。调整超时窗口属运营动作，不需改代码发版。

#### 读侧归属规则 + 受控例外

**归属规则（一句话）**：**本聚合读 → 域仓储**（ADR-0030 D2 / D6，**含本聚合的全租户运维扫描**）；
**跨聚合 / 报表 / 搜索 → `application/query/port/*QueryPort` + `infrastructure/query/*QueryAdapter`**。
判据是**读的归属与有无读模型分歧**，不是聚合种类。本模块订单与支付两侧都属第一种：订单读并入 `OrderRepository`；
支付的单笔直查走 `PaymentRepository.findByIdInTenant`，两个全租户扫描走同仓储的 `*AllTenants` default 方法。
因此本模块**当前不存在任何 `*QueryPort`**——出现跨聚合报表 / 搜索读时，才按规则新建端口与 `infrastructure/query` 实现。

**受控例外（本模块唯一形态，现有 3 个类）**：`adapter/schedule` 下的 `CancelExpiredOrderJob` / `CloseExpiredPaymentJob` /
`OrderPaymentInconsistencyJob` 直接注入**域仓储**调用其 `*AllTenants` 方法（`@TenantScope(ALL)` 或 Criteria
`disableTenantFilter()`）。这是 ADR-0030 §2 显式授权的**平台运维旁路**（定时线程无请求上下文，按"当前租户"扫描会退化为平台租户 `0`）。
它受三道门禁约束：`all_tenants_scan_only_by_schedule`（只有 `adapter.schedule` 能调 `*AllTenants`）、
`schedule_only_calls_all_tenants_repository_methods`（`adapter.schedule` 调域仓储时，方法名必须 `AllTenants` 结尾——
域仓储合并读写后自带 `save/update/delete`，光按包授权不够）、本模块
`ArchitectureTest#adapter_no_domain_repository_all_packages`（adapter 全包不得依赖 `domain.repository`，
**豁免范围仅限 `..adapter.schedule..` 整个包**，非按类名单）。
**其余入站适配器（web / rpc / messaging）不得复制此形态。**

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
| **入站适配器直连应用服务**（`@Capability` / `HandlerRegistry` 已随 Handler 内联移除） |**Adapter 直接调 ApplicationService**，无强制 UseCase 门面，见 E-3 应用用例（E-3.7 入口构件决策 / ADR-0028） |
| **领域事件 + AFTER_COMMIT** | 瘦载荷 `record` 事件 + `SpringDomainEventPublisher` + 应用层订阅 |
| **Outbox** | `bp_outbox` + `OrderOutboxPortAdapter` / `OrderOutboxRelay` / `OrderOutboxRelayJob` |
| **集成事件** | `OrderPaidIntegrationEvent` 与领域事件分离，经 Outbox 中继 |
| **多租户** | `TenantAggregateRoot` + 写侧 `findByIdInTenant`（`QueryParam` 条件，租户缺失即失败关闭）/ 读侧 SQL 显式 `tenant_id = :tenantId` + `X-Tenant-Id` 过滤器（生产由网关按 token claim 覆盖下发） |
| **JWT 鉴权** | `SecurityConfig` + `JwtAuthenticationFilter`（框架 `AbstractJwtAuthenticationFilter`）；共享密钥离线验签，**不回调 IAM** |
| **值对象 Money** | 金额规则集中在 `Money`（位于 `domain/model/shared/valueobject`，`Order` / `OrderItem` / **`Payment`** 共用——共享值对象独立成包，避免支付反向依赖订单包） |
| **读侧 Join** | `OrderRepository.findOrderWithItems`（`@Sql`，外置 `OrderRepository/findOrderWithItems.sql` 优先）扁平投影 → 域层投影 → `OrderDetailAssembler`（ADR-0030 合并，无独立读仓储/端口） |
| **扩展点** | 多实现价格计算器（VIP/企业/促销等） |
| **独立支付聚合** | `Payment`（`bp_payment`）+ 状态机 + 幂等/金额校验回调（见下） |
| **支付生命周期闭环** | 发起支付 → 渠道预下单 → 回调确认 → **查询**（`PaymentApplicationService.getById`）→ **超时关闭**（`CloseExpiredPaymentJob`）→ **退款**（`PaymentRefundedEvent` 驱动订单退款 + 释放库存） |
| **Feign + `InventoryGateway`** | ACL 出站调用 + 预留/确认/释放流程 |
| **CQRS 读侧** | **本模块无 `*QueryPort`**：订单/支付的本聚合读（`@Sql` / Criteria 读方法 + `domain/model/{order,payment}/projection`）与全租户运维扫描（`*AllTenants`）全部并入各自域仓储（ADR-0030）；域仓储不承载跨聚合报表 / Join。归属规则与受控例外见下节「读侧归属规则」 |
| **MQ / 定时任务 / RPC** | 入站适配器形态示例（MQ 消费端幂等落库、DLQ、消费指标见上节） |
| **幂等写（`Idempotency-Key`）** | core `IdempotencyService`（作用域键 `租户|用户|键|方法|路径`、SHA-256 指纹、同键异 body → 409 `COMMON_IDEMPOTENCY_CONFLICT`、TTL 24h）+ 控制器取头；同键同 body 重放同一响应（API 规范 §6.1/§8）。存储由 `IdempotencyStore` 适配（blueprint 样例 `IdempotencyPortAdapter` 落 `bp_idempotency_record`） |
| **授权（Scope）** | 端点声明 `@PreAuthorize("hasAuthority('order:orders:read'/'order:orders:write')")`；scope 由 IAM 随 token 下发（API 规范 §9.2） |
| **稳定错误码** | `common/BlueprintErrorCodes`（`BP_*`，登记于错误码登记 §6）；抛出统一用 `new BizException(HTTP 状态, 码 + ": " + 说明)`——`BizException(String)` 默认码是 **500**，会把 404/400 报成服务端故障。**翻译成 HTTP 状态 + 错误信封由 bone-web 的 `BoneWebExceptionAutoConfiguration` 提供**（该处理器此前没有任何注册入口，是死代码 → 业务异常直落 servlet 容器变 500；已在框架侧补 auto-configuration，模块无需扫描/导入） |
| **日志与链路** | `BoneRequestContextFilter`（MDC `traceId`/`tenantId`/`userId`/`httpRoute` + 每请求一条 `[API]` INFO + 回显 `X-Request-Id`）；身份在认证过滤器写入（安全链结束会清空 `SecurityContextHolder`）。**类名带 `Bone` 前缀是必需的**：Spring Boot 自动配置已注册名为 `requestContextFilter` 的 Bean，同名会启动即失败 |
| **DDL 真源** | 表结构只在仓库根 `bone-init.sql`；模块内无建表脚本且 `spring.sql.init.mode: never`，避免双轨 DDL 漂移 |
| **真实下单支付场景** | 独立 `Payment` 聚合（`bp_payment`）+ 状态机 + `PaymentGateway` 防腐 + **回调幂等**（`confirmSuccess`）+ 领域事件确认订单（跨聚合协作） |

## 若要「尽量简单」地抄一版

建议最小子集（示意，类名随域替换）：

1. `domain/{aggregate}/`：聚合根、实体、值对象、领域事件（按需）  
2. `domain/repository/`：写侧仓储接口（继承 SDK `Repository`，不堆查询方法）  
3. `application/`：语义化 ApplicationService（默认入口，写方法 + 读方法）；写意图需显式契约时才加 `command` 显式契约 + `command/handler`，读模型分歧时才加 `query/port` + `query/dto`  
4. 多租户隔离下沉仓储层（`OrderRepository.findByIdInTenant`，bone-core `QueryParam` + `Operator` 条件查询过滤，租户缺失即失败关闭—读侧 DSL 不得进 domain）；技术横切经 `application/port/out` 端口 + `infrastructure` 实现（如 `TenantPort` → `TenantPortAdapter`），由 `*ApplicationService` 经端口注入  
5. **写侧标准写法**：`repository.save(aggregate)` + `domainEventPublisher.publishFrom(aggregate)`（不设持久化端口）  
6. `adapter/web`：Controller、request/response DTO、Assembler  
7. `infrastructure/config` + `infrastructure/event`：元数据、Spring 配置、事件发布实现  

**需要真实支付时再加**：独立 `Payment` 聚合（`bp_payment`）+ `PaymentGateway` 防腐 + 回调幂等 `confirmSuccess` + `PaymentSucceededEvent` 驱动订单确认（跨聚合协作）——完整链路见本模块「真实下单支付场景」示范。

**先不要**：扩展点矩阵、Feign、MQ、Schedule、RPC、`@Capability` 注册表——等业务或集成真的需要再加。

## RocketMQ Outbox 中继

| 模式 | 配置 | 行为 |
|------|------|------|
| 开发默认 | `bone.blueprint.outbox.mq-enabled=false` | `LoggingOrderMessagePortAdapter` 打结构化日志 |
| MQ | `spring.profiles.active=mq` + NameServer | `RocketMqOrderMessagePortAdapter` + `OrderPaidIntegrationListener` |

> **Outbox 记录归属（工程折中，2026-08-27）**：`OrderOutboxRecord` 位于 `infrastructure/messaging/outbox/`（原 `domain/outbox/` 包已废弃）。它**不是业务聚合**，仅因复用 bone-metadata-sdk 的聚合持久化与主键回填机制而继承 `AggregateRoot`；无领域不变量（仅 PENDING→SENT/FAILED 技术状态）。若未来多模块需要通用 Outbox，应抽到独立基础设施组件，不再占用订单领域包。

### 可靠投递的三件套（缺一不可）

| 环节 | 实现 | 规范依据 |
|------|------|----------|
| **生产端原子** | `OrderOutboxPortAdapter`（`Propagation.MANDATORY`，强制与业务写同事务）+ 信封含 `eventId`/`eventType`/`topic`/`occurredAt`/`tenantId`/`traceId`/`schemaVersion` | 消息与事件规范 §3/§7 |
| **中继** | `OrderOutboxRelay`：PENDING → SENT；失败累计重试，**超限转投 `platform.dead_letter.v1`**（信封内已带 `topic`，重放无需回查表）；指标 `bone_mq_send_total{topic,status}` | §6/§9 |
| **消费端幂等** | `ConsumedEventPort` → `bp_processed_event`（`(consumer_group, event_id)` 唯一键**原子抢占**，非「先查后写」）；抢占与业务动作在**同一事务**（`OrderPaidConsumptionApplicationService`）——否则处理失败重试会被自己的幂等记录挡住而丢事件；指标 `bone_mq_consume_total{topic,status}` | §5/§9、ADR-0021 |

> Topic 命名遵循 §2（`{scope}.{domain}.{resource}_{action}.v{major}`），5 个集成事件已登记 §4 事件注册表。

### 幂等写与 `@Capability` 的两处口径（供其他模块照抄前先读）

**① 幂等写**：机制在 core `IdempotencyService`（作用域键 `租户|用户|键|方法|路径`、SHA-256
载荷指纹、24h TTL、冲突判定），入口在 adapter（`OrderController.create` 先问「该键是否已有响应」）。**应用层不出现 HTTP 类型**：
重放结果以协议无关的 `ReplayedResponse`（状态码 / `Location` / 信封体）表达，由 Controller 渲染成 `ResponseEntity`（E-10.1）；
平台既有 `StudioIdempotencyService` / `CatalogIdempotencyService` 是在 Handler 内直接收发 `ResponseEntity` 的存量形态，本模块不沿用。
存储用 MySQL（`bp_idempotency_record`）而非平台常见 Redis，因为本模块无 Redis 依赖，语义等价。

**② `@Capability` 在本模块已随 Handler 内联一并移除**：该注解 `@Target(TYPE)` 只能标在类上，天然适配「一个类一个用例」的
`*CommandHandler`，而 `OrderApplicationService` 这类**承载多个用例的服务没法逐个用例标注**——加一个类级注解会把
「多用例服务」误报成单一能力边界。若希望 ApplicationService 也能被编排/AI 发现，需要框架把注解开放到方法级（待办项，非本模块可解）。

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
