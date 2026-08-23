# DDD 补充条文与示例（与 Bone-DDD 主文档 4.3 配套）

> 与 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md)（**v4.3**）配套；门禁仍以主文档 §12 / §21 为准。本分册随主文档版本联动更新。

---

## 3.3.1 领域事件发布最小模式

1. 聚合内：`aggregate.addDomainEvent(new OrderPaidEvent(...))`（`AggregateRoot` / `TenantAggregateRoot`）。
2. `*CommandHandler` 在 `repository.save(aggregate)` 之后、事务提交前：调用 `DomainEventPublisher.publishAll(aggregate.getDomainEvents())`，再 `aggregate.clearDomainEvents()`。
3. 发布实现：`infrastructure` 中 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或 Outbox 表异步投递。
4. 发布失败抛 `InfrastructureException` / `SystemException`，**不**抛 `BizException`。

端口定义：`com.bone.core.domain.event.DomainEventPublisher`（bone-core）。

---

## 代码示例（最小链）

### Controller → Handler → 聚合

```java
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final CreateOrderCommandHandler createOrderHandler;

    @PostMapping("/orders")
    public ApiResponse<Long> create(@Valid @RequestBody CreateOrderReq req) {
        var cmd = OrderWebAssembler.toCommand(req);
        return ApiResponse.success(createOrderHandler.handle(cmd));
    }
}
```

### 多租户聚合根（仓库样板）

`bone-iam` 多租户聚合根（`TenantAggregateRoot`）：`Account`、`Role`、`AuditLog`（`setId` / `setTenantId`）；单租户：`Tenant`、`Permission`（`AggregateRoot` + `setId`）。域内审计仍用 `LocalDateTime`（阶段 2 见 [ADR-0011](../adr/0011-aggregate-root-inheritance.md)）。

### 聚合工厂与不变量

```java
public final class Order extends AuditableAggregateRoot<Long> {
    private OrderStatus status;

    public static Order create(Long id, Long customerId) {
        // id 由 SDK 统一生成（主文档 §18.1 当前契约）：调用方传 null，持久化后以 entity.getId() 为准
        var order = new Order();
        order.setId(id);  // AuditableAggregateRoot + 框架 Date 审计
        order.status = OrderStatus.DRAFT;
        order.addDomainEvent(new OrderCreatedEvent(id, customerId));
        return order;
    }

    public void pay() {
        if (status != OrderStatus.DRAFT) {
            throw new DomainException("ORDER_NOT_PAYABLE");
        }
        status = OrderStatus.PAID;
        addDomainEvent(new OrderPaidEvent(getId()));
    }
}
```

### 出站端口（ACL）

```java
// domain/gateway/AccessTokenIssuer.java
public interface AccessTokenIssuer {
    String issueAccessToken(Long accountId, String username, Long tenantId, List<String> scopes);
}

// application 仅依赖端口
@RequiredArgsConstructor
public class LoginCommandHandler {
    private final AccessTokenIssuer accessTokenIssuer;
    // ...
}
```

实现类：`infrastructure/security/JwtTokenService implements AccessTokenIssuer`（bone-iam 样板）。

### 读侧 QueryBuilder（application 或 infrastructure）

```java
QueryBuilder.from(OrderView.class)
    .where(q -> q.eq(OrderView::getTenantId, TenantContext.getTenantId()))
    .page(page, size);
```

### 读侧 QueryHandler（只读事务 + 端口依赖）

```java
@Component
@RequiredArgsConstructor
public class OrderPageQueryHandler {
    private final OrderReadPort orderReadPort;   // domain/gateway，读侧端口（§18.5）

    @Transactional(readOnly = true)
    public PageResult<OrderDTO> handle(OrderPageQuery qry) {
        return orderReadPort.page(qry.tenantId(), qry.page(), qry.size());
    }
}
```

### 值对象（record 不可变，§3.2）

```java
// 值对象：record 不可变、按值比较；禁止"可变值对象 + setter"
public record Money(long cents, String currency) {
    public Money {
        if (cents < 0) throw new DomainException("金额不能为负");
        if (currency == null || currency.isBlank()) throw new DomainException("币种不能为空");
    }

    public Money add(Money other) {
        if (!currency.equals(other.currency)) throw new DomainException("币种不一致");
        return new Money(cents + other.cents, currency);
    }
}
```

---

## 架构适应度仪表盘（建议季度复核）

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| `archunit_store` **新增**违规 | **0**（FreezingArchRule 门禁天然保证） | CI `ArchitectureTest`；季度复核时收缩基线（`allowStoreUpdate=true`） |
| `domain` 依赖外层 | 0 | `domainMustNotDependOnOuterLayers` |
| 自建 `BusinessException` | 0 | `noCustomBusinessException` |
| 仓储 `findBy*And*` 方法 | 0 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` |
| 单元测试行覆盖率（应用模块） | ≥ 80%（L2 ≥ 85%） | JaCoCo |

> **口径说明（2026-08 修正）**：违规治理目标是「**新增为 0 + 季度收缩基线**」（与主文档 §0.2「基线只收缩不扩张」一致），而非按比例环比——当违规数趋近 0 时比例指标失去约束力。

---

## P0-2 判定标准（Handler vs 聚合）

若规则需要访问**聚合自身字段或子实体状态**以判定是否允许操作 → **聚合内规则**（`DomainException`）。  
若仅为用例级前置（参数组合、ACL 已判定、跨聚合策略拒绝）→ **Handler** 可校验（`BizException`）。

---

## §18.1 ID 生成备注（2026-08，与主文档对齐）

- **现状**：`bone-metadata-sdk` 的 `insert` / `save` 均**无条件重新生成主键**（`@GeneratedValue(DISTRIBUTED_ID)` 由 SDK 内部生成器产生），忽略应用层预分配值，并以新 id 回填实体。
- **用法**：聚合工厂的 `id` 参数传 `null`；创建 handler 用 `Long id = repository.insert(entity)`（或 save 后 `entity.getId()`）作为返回值回传调用方。
- **反例（已修复）**：`studio-generator` 曾预分配 `DistributedIdGenerator` id + `insert` + 返回预分配值，导致返回值与落库值不一致（2026-08 修复为 insert 返回值）。
- **未来改进**：若业务需保留外部 id（迁移/导入），SDK 需扩展（如 `insertWithId` 或 `save` 支持 id 非空时不重新生成），改造后本节与主文档 §18.1 同步更新。
