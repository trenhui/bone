package com.bone.blueprint.application;

import com.bone.blueprint.application.command.CancelOrderCommand;
import com.bone.blueprint.application.command.CreateOrderCommand;
import com.bone.blueprint.application.command.DeliverOrderCommand;
import com.bone.blueprint.application.command.ShipOrderCommand;
import com.bone.blueprint.application.port.out.PricingPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.port.OrderQueryPort;
import com.bone.blueprint.application.query.projection.OrderHeadProjection;
import com.bone.blueprint.application.query.projection.OrderWithItemsProjection;
import com.bone.blueprint.application.query.support.OrderDetailAssembler;
import com.bone.blueprint.application.query.support.OrderSummaryAssembler;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.repository.OrderItemRepository;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单应用层统一门面（Facade）——所有订单用例的唯一入口。
 *
 * <p><b>适配器只依赖这一个类</b>——Controller / RPC / JobHandler 不需要知道应用层内部实现细节， 读侧走 QueryPort 还是 Repository。
 *
 * <p>内部按关注点选择实现方式：
 *
 * <ul>
 *   <li><b>写操作</b>（create / cancel / ship / deliver）——聚合加载 → 领域方法 → 保存发布。 create
 *       涉及库存校验、扩展点定价、明细持久化等多依赖编排， 但代码量可控（~40 行逻辑）， 直接内联而非再拆一个 Handler 类。
 *   <li><b>读操作</b>（getById / page）——通过 {@link OrderQueryPort} 走读侧模型，{@code @Transactional(readOnly
 *       = true)}。 CQRS 边界在 <strong>port</strong> 层（QueryPort vs Repository）， 不在
 *       <strong>class</strong> 层。
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class OrderApplicationService {

  // ========== 写侧依赖 ==========
  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final InventoryGateway inventoryGateway;
  private final PricingPort pricingService;
  private final DomainEventPublisher domainEventPublisher;
  private final TenantPort tenantProvider;

  // ========== 读侧依赖 ==========
  private final OrderQueryPort orderQueryPort;

  // ===================== 写操作 =====================

  /**
   * 创建订单。
   *
   * <p><b>库存策略（读同步、写异步）</b>：
   *
   * <ul>
   *   <li>{@code checkStock} 是同步<strong>读</strong>校验：超卖必须在下单前同步拦截，才能向用户返回可见的 「库存不足」错误，故保留在事务内。
   *   <li>{@code reserveStock} 是远程<strong>写</strong>：已从本事务移除。远程写置于本地事务会产生「库存 悬挂」（远程预留成功而本地回滚 →
   *       预留无对应订单），且远程慢会拖长事务、占用连接池。改由 {@code OrderCreatedEvent} 的 AFTER_COMMIT 订阅器异步执行 + 失败补偿对账。
   * </ul>
   */
  @Transactional
  public Long create(CreateOrderCommand command) {
    for (CreateOrderCommand.OrderItemDto dto : command.items()) {
      if (!inventoryGateway.checkStock(dto.productId(), dto.quantity())) {
        // 用业务码而非 BizException.of(message)：后者的默认码是 500，会把「业务校验不通过」报成服务端故障，
        // 污染 5xx 告警与 SLO 口径（错误码登记 §6 已点名这个坑）。
        throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_STOCK_INSUFFICIENT, dto.productId());
      }
    }

    // 身份在构造期确定（ADR-0019 目标态）：SDK 尊重调用方预分配的非空 id，落库后 id 不变，
    // 因此明细外键与事件载荷可直接使用该 id，无需在落库后回填。
    long provisionalOrderId = DistributedIdGenerator.generateLongId();
    long tenantId = tenantProvider.currentTenantId();

    List<OrderItem> items =
        command.items().stream()
            .map(
                dto ->
                    OrderItem.create(
                        DistributedIdGenerator.generateLongId(),
                        provisionalOrderId,
                        dto.productId(),
                        dto.productName(),
                        dto.quantity(),
                        dto.unitPrice()))
            .collect(Collectors.toList());

    Order order = Order.create(provisionalOrderId, tenantId, command.customerId(), items);

    // 扩展点定价由应用层编排：算出最终金额后交给聚合，聚合不感知扩展点接口（领域层只认 Money）。
    order.applyPricing(pricingService.calculateFinalPrice(order.getTotalMoney(), tenantId));

    orderRepository.save(order);
    Long persistedOrderId = order.getId();

    // 明细无级联：须显式逐条持久化。
    for (OrderItem item : items) {
      orderItemRepository.save(item);
    }
    domainEventPublisher.publishFrom(order);
    return persistedOrderId;
  }

  @Transactional
  public void cancel(CancelOrderCommand command) {
    long tenantId = resolveTenantId(command.tenantId());
    Order order =
        Optional.ofNullable(orderRepository.findByIdInTenant(command.orderId(), tenantId))
            .orElseThrow(
                BlueprintErrors.supplier(BlueprintErrorCodes.ORDER_NOT_FOUND, command.orderId()));
    order.cancel();
    orderRepository.saveWithVersionCheck(order);
    domainEventPublisher.publishFrom(order);
  }

  @Transactional
  public void ship(ShipOrderCommand command) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    command.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(
                BlueprintErrors.supplier(BlueprintErrorCodes.ORDER_NOT_FOUND, command.orderId()));
    try {
      order.ship();
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, ex.getMessage(), ex);
    }
    orderRepository.saveWithVersionCheck(order);
  }

  @Transactional
  public void deliver(DeliverOrderCommand command) {
    Order order =
        Optional.ofNullable(
                orderRepository.findByIdInTenant(
                    command.orderId(), tenantProvider.currentTenantId()))
            .orElseThrow(
                BlueprintErrors.supplier(BlueprintErrorCodes.ORDER_NOT_FOUND, command.orderId()));
    try {
      order.deliver();
    } catch (DomainException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_CONFLICT, ex.getMessage(), ex);
    }
    orderRepository.saveWithVersionCheck(order);
  }

  // ===================== 读操作 =====================

  /** 查询订单详情（含明细行，走读侧 QueryPort）。 */
  @Transactional(readOnly = true)
  public OrderDto getById(long orderId) {
    long tenantId = tenantProvider.currentTenantId();
    List<OrderWithItemsProjection> rows = orderQueryPort.findOrderWithItems(tenantId, orderId);
    return OrderDetailAssembler.fromRows(rows);
  }

  /** 分页查询订单（走读侧 QueryPort）。 */
  @Transactional(readOnly = true)
  public PageResult<OrderDto> page(
      Long customerId, String status, Integer pageNum, Integer pageSize) {
    long tenantId = tenantProvider.currentTenantId();
    OrderStatus parsedStatus = parseStatus(status);
    int page = pageNum != null ? pageNum : 1;
    int size = pageSize != null ? pageSize : 10;

    PageResult<OrderHeadProjection> result =
        orderQueryPort.findOrderPage(tenantId, customerId, parsedStatus, page, size);

    List<OrderDto> records =
        result.getRecords().stream().map(OrderSummaryAssembler::fromRow).toList();
    return PageResult.of(records, result.getTotal(), page, size);
  }

  private static OrderStatus parseStatus(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return OrderStatus.valueOf(status);
    } catch (IllegalArgumentException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.ORDER_STATUS_INVALID, status);
    }
  }

  private long resolveTenantId(Long explicitTenantId) {
    return explicitTenantId != null ? explicitTenantId : tenantProvider.currentTenantId();
  }
}
