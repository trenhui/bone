package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.model.order.Order;
import com.bone.blueprint.domain.model.shipment.event.ShipmentShippedEvent;
import com.bone.blueprint.domain.model.shipment.event.ShipmentSignedEvent;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 发货单事件 → 订单状态联动（AFTER_COMMIT）。
 *
 * <p><b>修复的真实缺陷</b>：在加入本处理器之前，发货链路（{@code ShipmentApplicationService}）
 * 只推进发货单状态，从不触碰订单状态，两套状态机完全割裂。生产数据实测后果：
 *
 * <ul>
 *   <li>发货单已 {@code SIGNED}，订单仍停在 {@code CREATED}/{@code PAID} ⇒ 前端订单列表显示「未发货」， 客服与用户看到的事实不一致；
 *   <li>更严重的是<b>已取消（CANCELLED）的订单也能建发货单并走完发货签收</b>——因为 {@code Order.ship()}
 *       里「只有已支付订单可以发货」的状态守卫<b>从未被调用</b>。
 * </ul>
 *
 * <p><b>为何不能直接改状态、必须容忍不一致</b>：发货单与订单是两个聚合，一事务一聚合是本仓硬约束。 本处理器在发货事务提交后推进订单，是最终一致（不是强一致）。
 * 若订单状态不允许推进（如已取消的订单被补发），<b>不回滚发货单</b>——货已经发出， 那是物理事实；改为记WARN 留痕交由人工处理，与「渠道回传失败不回退发货状态」同一原则。
 *
 * <p><b>幂等</b>：事件只在状态真正迁移时记录一次；即便重复投递， {@code Order.ship()} 的 {@code status != PAID} 守卫也会拦住二次迁移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShipmentOrderSyncEventHandler {

  private final OrderRepository orderRepository;

  /** 已交运 ⇒ 订单推进到 SHIPPED。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(ShipmentShippedEvent event) {
    advanceOrder(event.orderId(), event.tenantId(), event.shipmentId(), "发货", Order::ship);
  }

  /** 已签收 ⇒ 订单推进到 DELIVERED。 */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(ShipmentSignedEvent event) {
    advanceOrder(event.orderId(), event.tenantId(), event.shipmentId(), "签收", Order::deliver);
  }

  /**
   * 加载订单 → 推进状态 → 保存；任何异常都只记日志不外抛。
   *
   * <p><b>为何吞掉异常</b>：本方法在 AFTER_COMMIT 执行，抛异常不会回滚已提交的发货事务， 只会让Spring
   * 打印未处理异常并可能干扰调用方。若订单已被取消/退款，状态守卫会抛 {@code DomainException}——这是<b>预期内</b>的业务状态（补发已取消订单），不是系统故障。
   *
   * <p><b>★为何必须 {@code TenantContextRunner.callAs}</b>：AFTER_COMMIT 阶段已脱离 HTTP 请求线程， {@code
   * TenantContext} 通常为 null，而 SDK 写路径按 ADR-0029 <b>失败关闭</b>（缺租户直接抛异常）。 若不显式切租户，本处理器会整条静默失效——只留一行
   * warn 很难定位， 表现为「联动从未生效但无报错」。与 {@code ChannelBuyerObservedEventHandler} / {@code
   * PaymentSucceededEventHandler} 是同一既定范式。
   */
  private void advanceOrder(
      Long orderId,
      Long tenantId,
      Long shipmentId,
      String action,
      java.util.function.Consumer<Order> transition) {
    try {
      // ★必须显式切租户：AFTER_COMMIT 阶段已脱离 HTTP 请求上下文，TenantContext 通常为 null，
      //   而 SDK 写路径按 ADR-0029 失败关闭（缺租户直接抛）⇒ 不切租户会让整条联动静默失效
      //   （日志只留一行 warn，极难定位）。与 ChannelBuyerObservedEventHandler /
      //   PaymentSucceededEventHandler 是同一范式。
      //   判定用 < 0（外加 null）而非 <= 0：tenantId=0 是合法的平台租户。
      if (tenantId == null || tenantId < 0) {
        log.warn(
            "发货联动跳过：事件缺少有效 tenantId | orderId={} | shipmentId={} | action={}",
            orderId,
            shipmentId,
            action);
        return;
      }
      Boolean moved =
          TenantContextRunner.callAs(
              tenantId,
              () -> {
                Order order = orderRepository.findById(orderId);
                if (order == null) {
                  return false;
                }
                transition.accept(order);
                orderRepository.update(order);
                log.info(
                    "发货联动订单状态推进成功 | orderId={} | shipmentId={} | action={} | status={}",
                    orderId,
                    shipmentId,
                    action,
                    order.getStatus());
                return true;
              });
      if (Boolean.FALSE.equals(moved)) {
        log.warn(
            "发货联动跳过：订单不存在 | orderId={} | shipmentId={} | action={}", orderId, shipmentId, action);
      }
    } catch (RuntimeException ex) {
      // 不回退发货单：货已发出是物理事实，改由人工核对。
      log.warn(
          "发货联动订单状态推进失败（发货单状态不回退，留痕待人工处理） | orderId={}"
              + " | shipmentId={} | action={} | reason={}",
          orderId,
          shipmentId,
          action,
          ex.getMessage());
    }
  }
}
