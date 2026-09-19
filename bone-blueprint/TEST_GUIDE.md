# Bone-Blueprint 测试指南

> 本文件只描述**当前**实现。目录与类名以仓库实际内容为准，改结构时同步更新本文件。
> 规范真源：[doc/architecture/Bone-DDD-最终实践方案.md](../doc/architecture/Bone-DDD-最终实践方案.md)。

## 概述

`bone-blueprint` 是 Bone DDD 规范的**参考样板（L3）**：订单 / 支付两个限界上下文，四层结构
（`adapter` / `application` / `domain` / `infrastructure`），CQRS 读写分离、Outbox 可靠投递、
多租户隔离、扩展点定价、幂等写与回调验签。

测试共 **33 个测试类**，分三层：领域纯单测、应用/适配器协作测试、ArchUnit 架构门禁。

## 项目结构

```text
bone-blueprint/
├── src/main/java/com/bone/blueprint/
│   ├── BoneBlueprintApplication.java
│   ├── adapter/                          # 入站适配器（只做协议转换与路由）
│   │   ├── web/       {controller, dto/{request,response}, assembler}
│   │   ├── rpc/       {controller, dto/{request,response}, assembler}
│   │   ├── mq/listener/                  # MQ 消费（包名待收敛为 messaging，见 E-13.5）
│   │   └── schedule/                     # 定时任务 *Job
│   ├── application/                      # 用例编排（*ApplicationService 平铺在根目录）
│   │   ├── OrderApplicationService / PaymentApplicationService（ADR-0028 应用服务化，已无 *CommandHandler）
│   │   ├── command/                      # *Command / *Result（平铺，不建 cmd/ 子包）
│   │   ├── integration/consumer/         # 入站集成事件消费（幂等抢占 + 业务动作）
│   │   ├── event/                        # *EventHandler（领域事件订阅）
│   │   ├── event/integration/            # *IntegrationEvent + IntegrationEnvelope
│   │   ├── query/{dto, projection, port, support}
│   │   ├── util/                          # 应用层纯函数工具（DomainEvents）
│   │   ├── event/support/                 # 事件处理器共用骨架（OrderItemInventoryExecutor）
│   │   └── port/out/                     # 出站端口（统一 *Port 后缀）
│   ├── domain/                           # domain/{聚合} 平铺形态（README 已登记）
│   │   ├── order|payment/{, event/, valueobject/}
│   │   ├── shared/{valueobject, exception}
│   │   ├── repository/                   # 写侧聚合仓储（*Repository）
│   │   ├── gateway/                      # 外部业务能力端口（*Gateway）
│   │   └── extension/order/              # 定价策略业务端口
│   ├── infrastructure/                   # 出站适配器（*Adapter / *Impl）
│   │   ├── {query, messaging/{outbox,idempotency}, idempotency, context, event,
│   │   │    extension/order, gateway/{payment,mock}, observability, security, config/…}
│   └── common/BlueprintErrorCodes.java
├── src/main/resources/
│   ├── application.yml / application-{dev,mq,prod}.yml
│   └── sql/order/findOrderWithItems.sql  # 读侧投影 SQL（DDL 真源是仓库根 bone-init.sql）
└── src/test/java/com/bone/blueprint/     # 见下节
```

## 运行测试

```bash
cd bone-blueprint

mvn clean compile          # 编译
mvn test                   # 全量测试
mvn test -Dtest=ArchitectureTest                # 架构门禁
mvn test -Dtest='*ApplicationServiceTest'       # 应用层
mvn test -Dtest='*OrderPriceCalculatorTest'     # 定价扩展点
mvn test -Dtest='OrderOutboxRelayTest'          # Outbox 中继
```

首次需先安装共享 ArchUnit 规则库（在仓库根执行）：

```bash
mvn install -pl bone-framework/bone-architecture-test -am -DskipTests
```

## 测试分层

### 架构门禁（`ArchitectureTest`，27 条，须 0 违规）

本模块是参考样板，**不 freeze**——所有规则必须真实全绿。规则来自共享库
`BoneDddArchRules`（`bone-framework/bone-architecture-test`），主要覆盖：

| 关注点 | 代表规则 |
|--------|----------|
| 依赖方向（CORE-02） | `domainMustNotDependOnOuterLayers`、`applicationMustNotDependOnInfrastructure` |
| 聚合身份 / 租户归属（E-2） | `outerLayersMustNotMutateAggregateIdentity` |
| 租户取值收敛（E-2） | `businessLayersMustNotReadTenantContextDirectly`、`all_tenants_scan_only_by_schedule` |
| 读侧 DSL 位置（E-4.2） | `readSideDslOnlyInQueryLayer` |
| 一事务一聚合（CORE-06 / E-5.1） | `oneAggregatePerTransaction` |
| 入口边界（CORE-04 / ADR-0028） | `adapterControllersMustNotDependOnGodObjects`、禁止 Handler 与 ApplicationService 套娃 |
| 跨上下文边界（E-1.3 / P-2.4） | `noCrossContextDomainDependency`、`noCrossContextModelDependency` |
| 领域行为归属（CORE-03 / E-6.4） | `applicationServicesMustNotOwnDomainRules` |
| 持久化栈与生命周期（E-5.4） | `applicationSaveMustPairWithPublishOrExempt`、`springComponentBeanNamesMustBeUnique` |

其余规则清单与分级见
[bone-architecture-test/README.md](../bone-framework/bone-architecture-test/README.md) 与规范
[G-1.1 Hard gate](../doc/architecture/Bone-DDD-最终实践方案.md#g-1-1-hard-gate)。

### 规则类单测（根目录）

- `AggregateIdentityRuleEffectivenessTest` —— 验证聚合身份规则确实拦得住（避免门禁空转）
- `AggregatePureUnitTestCoverageTest` —— 聚合纯单测覆盖检查
- `ConfigKeysContractTest` —— 配置键契约：源码引用的 `bone.*` 占位符必须在 `application*.yml` 有定义（防"键名写错 → 静默回落默认值"），
  且 `bone.blueprint.schedule.*` 的定义键必须被引用（防死配置）

### 领域层（`domain/`）

| 测试 | 锁定内容 |
|------|----------|
| `OrderTest` / `OrderItemTest` | 订单状态机（`CREATED → PAID → SHIPPED → DELIVERED`，分支 `CANCELLED` / `REFUNDED`）与拒绝路径 |
| `PaymentTest` | 支付单状态机、回调幂等、金额一致性、退款幂等 |
| `MoneyTest` | 金额值对象运算与不变量 |
| `RepositoryTenantIsolationTest` | 写侧仓储的租户隔离（缺失租户即失败关闭） |

### 应用层（`application/`）

| 测试 | 锁定内容 |
|------|----------|
| `OrderApplicationServiceTest` | 创建订单（库存校验、定价编排、明细逐条落盘）、发货 / 送达 / 取消 |
| `PaymentApplicationServiceTest` | 发起支付（两段式事务 + 渠道预下单）、回调（验签 + 幂等）、退款、超时关闭 |
| `integration/consumer/OrderPaidIntegrationEventConsumerTest` | 入站集成事件：幂等抢占 + 业务动作 + 事务边界 |
| `event/*EventHandlerTest` | 领域事件订阅：`REQUIRES_NEW` / `AFTER_COMMIT` 语义与失败留痕 |
| `query/support/OrderDetailAssemblerTest` | 读侧投影 → DTO 装配 |
| `support/IdempotencyServiceTest` | `Idempotency-Key` 幂等（同键同 body 重放、同键异 body 409、TTL 24h） |

### 适配器层（`adapter/`）

| 测试 | 锁定内容 |
|------|----------|
| `web/controller/OrderControllerContractTest` | HTTP 契约：`201 + Location`、错误信封与稳定错误码 |
| `web/controller/PaymentControllerCallbackSourceTest` | 回调来源 IP 白名单：空名单放行、`X-Forwarded-For` 取首段、未命中 `403` **且不进应用层** |
| `web/assembler/OrderAssemblerTest` | 协议 DTO ↔ 应用对象转换 |
| `schedule/*JobTest` | 定时任务：**异步租户显式传递**（E-2）、全租户扫描仅限 schedule、Outbox 中继、钱货一致性对账 |

### 基础设施层（`infrastructure/`）

| 测试 | 锁定内容 |
|------|----------|
| `extension/order/*OrderPriceCalculatorTest` | 定价扩展点（默认 / VIP / 企业 / 促销 / 会员） |
| `messaging/outbox/OrderOutboxPortAdapterTest` | Outbox 追加：PENDING 记录、租户取事件自带值、载荷为集成事件而非领域事件 |
| `messaging/outbox/OrderOutboxEnvelopeFactoryTest` | 信封必填字段与 ISO-8601 时间 |
| `messaging/outbox/OrderOutboxRelayTest` | PENDING → SENT、超限转死信 |
| `idempotency/IdempotencyPortAdapterTest` | 幂等快照落库与 TTL 过期语义 |
| `gateway/payment/MockPaymentSignaturePortAdapterTest` | 回调验签（模拟 HMAC） |
| `observability/BoneRequestContextFilterTest` | MDC 字段、访问日志用 URI 模板而非原始路径 |

## 数据库初始化

DDL **真源唯一**是仓库根 `bone-init.sql`；模块内不提供建表脚本，`spring.sql.init.mode: never`。

```bash
mysql -u root -p bone < bone-init.sql
```

## 启动应用

```bash
mvn spring-boot:run      # 默认端口 8082
```

所有 `/api/**` 需 `Authorization: Bearer <token>`（密钥须与 IAM 一致，详见 README「鉴权」一节）。

## API 端点

### 订单（`/api/v1/orders`）

```bash
# 创建（Idempotency-Key 可选，支持幂等重放）→ 201 Created + Location: /api/v1/orders/{id}
POST /api/v1/orders

# 分页 / 详情
GET /api/v1/orders?customerId=1&status=PAID&pageNum=1&pageSize=10
GET /api/v1/orders/{id}

# 状态流转
POST /api/v1/orders/{id}/cancel
POST /api/v1/orders/{id}/ship
POST /api/v1/orders/{id}/deliver
```

### 支付（`/api/v1/payments`）

```bash
POST /api/v1/payments/initiate          # 发起支付 → 返回支付链接
POST /api/v1/payments/callback          # 渠道回调（验签 + 幂等）
GET  /api/v1/payments/{paymentId}       # 查询支付单
POST /api/v1/payments/{paymentId}/refund
```

RPC 入站（`/api/rpc/orders`）：`POST /`、`GET /{orderId}`。

契约文件：[doc/architecture/openapi/blueprint-orders-v1.yaml](../doc/architecture/openapi/blueprint-orders-v1.yaml)。
