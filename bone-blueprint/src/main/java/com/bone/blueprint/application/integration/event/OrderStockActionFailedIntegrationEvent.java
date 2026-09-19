package com.bone.blueprint.application.integration.event;

import java.time.Instant;

/**
 * 库存动作失败集成事件（跨边界发布语言 / 契约）。
 *
 * <p><b>为何在 application 包</b>：与 {@link OrderPaidIntegrationEvent} 同层——它是 {@code OrderOutboxPort}
 * 出站端口的契约载荷，三处（写侧适配器、MQ 消费端、应用消费服务）都要依赖它， 放在 application 使依赖保持向内（{@code infrastructure/adapter →
 * application}）。
 *
 * <p><b>为何是集成事件而非领域事件</b>：库存预留/扣减失败发生在 application 层对 {@code InventoryGateway}
 * 的<strong>远程调用</strong>， 不源于任何聚合状态迁移，没有持有它的聚合。它是<strong>可观测性 / 补偿</strong>事件，故直接以集成事件契约入 Outbox，
 * 不经由领域事件中转（与「支付/订单领域事件 → 集成事件」的转换路径不同，但落 Outbox 的语义一致：使断点可观测、可接告警/工单）。
 *
 * <p><b>补齐最终一致链路</b>：原 {@code OrderItemInventoryExecutor} 的库存动作失败路径<strong>仅打日志</strong>，
 * 库存与订单会静默不一致、直到超卖才暴露；本事件使其与 {@code OrderPaymentInconsistentIntegrationEvent}（钱货不一致）
 * 一样成为可观测、可补偿的事实。
 */
public record OrderStockActionFailedIntegrationEvent(
    Long orderId,
    Long tenantId,
    Long productId,
    Integer quantity,
    String actionName,
    String reason,
    Instant occurredAt,
    String schemaVersion)
    implements IntegrationEnvelope {

  /** 由库存动作失败事实构造载荷（其余集成事件的 {@code fromDomain} 同形态）。 */
  public static OrderStockActionFailedIntegrationEvent fromDomain(
      Long orderId,
      Long tenantId,
      Long productId,
      Integer quantity,
      String actionName,
      String reason,
      Instant occurredAt) {
    return new OrderStockActionFailedIntegrationEvent(
        orderId,
        tenantId,
        productId,
        quantity,
        actionName,
        reason,
        occurredAt,
        CURRENT_SCHEMA_VERSION);
  }
}
