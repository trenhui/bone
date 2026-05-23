# DDD 补充条文与示例（v4.1）

> 与 [Bone-DDD-最终实践方案.md](../Bone-DDD-最终实践方案.md) 配套；门禁仍以主文档 §12 / §21 为准。

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
    public ApiResponse<Long> create(@Valid @RequestBody CreateOrderRequest req) {
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
public class LoginHandler {
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

---

## 架构适应度仪表盘（建议季度复核）

| 指标 | 目标 | 测量方式 |
|------|------|----------|
| `archunit_store` 违规条数 | 环比 ≤ 90% | 各模块 `archunit_store/*.txt` 行数 |
| `domain` 依赖外层 | 0 | `domainMustNotDependOnOuterLayers` |
| 自建 `BusinessException` | 0 | `noCustomBusinessException` |
| 仓储 `findBy*And*` 方法 | 0 | `domainRepositoriesShouldOnlyDeclareWhitelistedMethods` |
| 单元测试行覆盖率（应用模块） | ≥ 80%（L2 ≥ 85%） | JaCoCo |

---

## P0-2 判定标准（Handler vs 聚合）

若规则需要访问**聚合自身字段或子实体状态**以判定是否允许操作 → **聚合内规则**（`DomainException`）。  
若仅为用例级前置（参数组合、ACL 已判定、跨聚合策略拒绝）→ **Handler** 可校验（`BizException`）。
