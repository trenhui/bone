package com.bone.blueprint.application.event;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.model.order.event.OrderCancelledEvent;
import com.bone.core.tenant.context.TenantContextRunner;
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
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledEventHandler {

  private final InventoryGateway inventoryGateway;

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
    TenantContextRunner.runAs(
        event.tenantId(), () -> inventoryGateway.releaseStock(event.orderId()));
  }
}
