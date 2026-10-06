package com.bone.blueprint.application.event;

import com.bone.blueprint.application.event.support.OrderItemInventoryExecutor;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.event.OrderCancelledEvent;
import com.bone.blueprint.domain.model.order.projection.OrderWithItemsProjection;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 订单取消后释放库存预留（AFTER_COMMIT）。
 *
 * <p><b>★为何必须 {@code TenantContextRunner.callAs}（2026-10-06 修复）</b>： SDK 写路径按 ADR-0031 D3
 * <b>失败关闭</b>——缺租户直接抛 {@code tenantId must not be null: 异步入口必须显式声明租户}。 而 {@code AFTER_COMMIT} 阶段已脱离
 * HTTP 请求线程，{@code TenantContext} 通常为 null。
 *
 * <p><b>为何此前一直没暴露</b>：{@code InventoryGateway} 当前装配的是 {@code MockInventoryGatewayAdapter}（仅 {@code
 * traceNoop} 打日志、不碰数据库）， 所以缺租户也不会报错。一旦换上真实库存实现（要读写 {@code bp_inventory}），
 * 这条处理器就会<b>整条静默失效</b>——表现是「订单已取消但库存永远不释放、超卖不收敛」， 编译通过、单测全绿、只有生产超卖后才暴露。
 *
 * <p>这正是门禁 {@code scripts/ci/check-async-tenant-context.py} 存在的原因： 它专门拦「AFTER_COMMIT
 * 处理器直接调端口/仓储却没切租户」， 且该缺陷<b>已用负向探针实测复现</b>（把 callAs 的租户换成 null，联动立刻失效）。
 *
 * <p><b>释放为何走行粒度并收敛到 {@link OrderItemInventoryExecutor}（2026-10-07）</b>：预留/扣减两条链路都是 「读明细 →
 * 逐行远程动作」，唯独释放曾是整单一次调用（{@code releaseStock(orderId)}）。整单粒度把一个
 * 必然要回答的问题留给了真实库存实现——「该退多少」？按该单全部预留行退，则已支付（预留被 confirm 消费）
 * 的单会被凭空回补库存；按剩余预留退，则支付前取消（正常路径）什么也退不掉。歧义放在接口上，坑只会 显现在真实实现接入后。现与预留/扣减共用同一执行骨架（读侧投影 → 逐行 {@code
 * releaseStockLine}）， 三条链路同构，单侧暴露形态在结构上不可能再出现。
 *
 * <p><b>REFUNDED → CANCELLED 的盲区（P2-8 允许的 lawful 跃迁）</b>：已退款订单的预留早在支付确认时被 {@code confirmStock}
 * 消费，此时收到取消事件，逐行释放会白做。Executor 的「明细为空」留痕对它不适用 （明细非空、只是无可释放预留），故在订阅侧显式拦截：非 REFUNDED 才执行释放，并把该口径与
 * {@code Order.cancel} 的 javadoc（允许跃迁的理由）互链。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler {

  private final InventoryGateway inventoryGateway;
  private final OrderRepository orderRepository;

  /**
   * 释放库存预留。
   *
   * <p>判定用 {@code < 0}（外加 null）而非 {@code <= 0}：{@code tenantId=0} 是合法的平台租户， 当成脏数据会让平台租户的取消单永远不释放库存。
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(OrderCancelledEvent event) {
    log.info("订单已取消，释放库存预留: orderId={}", event.orderId());
    if (event.tenantId() == null || event.tenantId() < 0) {
      log.warn("释放库存预留跳过：事件缺少有效 tenantId（订单取消后库存将不释放，需人工处理）: orderId={}", event.orderId());
      return;
    }
    if (isRefunded(event)) {
      // REFUNDED → CANCELLED：预留已被 confirmStock 消费，没有可释放的预留，释放是空动作。
      // 不拦会让真实库存实现把「无可释放」当异常上报，或 worse，把整单回补——取决于实现者对
      // 旧整单签名的理解。显式跳过并留痕。
      log.info(
          "订单为已退款（REFUNDED → CANCELLED 合法跃迁，见 Order.cancel javadoc），无可释放预留，跳过库存释放: orderId={}",
          event.orderId());
      return;
    }
    TenantContextRunner.runAs(
        event.tenantId(),
        () ->
            OrderItemInventoryExecutor.forEachItem(
                orderRepository,
                event.tenantId(),
                event.orderId(),
                "释放",
                inventoryGateway::releaseStockLine,
                null));
  }

  /** 当前订单是否已是 REFUNDED（取消为退款后合法跃迁时，库存无可释放）。读失败按非退款处理（保守释放，与旧行为一致）。 */
  private boolean isRefunded(OrderCancelledEvent event) {
    try {
      List<OrderWithItemsProjection> rows =
          TenantContextRunner.callAs(
              event.tenantId(), () -> orderRepository.findOrderWithItems(event.orderId()));
      // 扁平行集：头字段逐行重复，取任一行判定即可（无行时按非退款处理，保守释放）。
      return rows != null
          && !rows.isEmpty()
          && OrderStatus.REFUNDED.name().equals(rows.get(0).getStatus());
    } catch (Exception ex) {
      log.warn("查询订单状态失败，按非退款订单继续释放: orderId={}", event.orderId(), ex);
      return false;
    }
  }
}
