package com.bone.blueprint.application.integration.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 订单已支付集成事件（跨边界发布语言 / 契约）。
 *
 * <p><b>为何在 application 包</b>：集成事件是 application 层出站端口（{@code
 * OrderOutboxPort}）的<strong>契约载荷</strong>。 写侧由 infrastructure 适配器按端口契约构造并落 Outbox，读侧由 adapter 的 MQ
 * 消费端与 application 的消费型应用服务共同解析—— 三处都要依赖它，放在 application 使依赖保持向内（{@code infrastructure/adapter →
 * application}）。它不含领域业务行为、 不参与聚合状态迁移，故不下沉 domain；若上提到 infrastructure，则会逼出 {@code adapter →
 * infrastructure} 的反向依赖。
 */
public record OrderPaidIntegrationEvent(
    Long orderId,
    Long tenantId,
    Long customerId,
    BigDecimal amount,
    Instant occurredAt,
    String schemaVersion)
    implements IntegrationEnvelope {

  /**
   * 由领域事件的字段构造载荷（其余集成事件的 {@code fromDomain} 同形态）。
   *
   * <p><b>为何入参是散装标量，而不是直接接收 {@code domain.*Event}</b>：集成事件是跨边界<strong>契约</strong>，
   * 若让它接收领域事件对象，契约就会在编译期依赖 domain 包——下游模块只想依赖契约时被迫传递依赖本上下文的领域模型， 契约的边界价值随之消失。代价是这些同类型参数（多是若干个
   * {@code Long}）顺序错位编译器不会报错， 调用点（{@code OrderOutboxPortAdapter}）必须逐个核对实参顺序。
   */
  public static OrderPaidIntegrationEvent fromDomain(
      Long orderId, Long tenantId, Long customerId, BigDecimal amount, Instant occurredAt) {
    return new OrderPaidIntegrationEvent(
        orderId, tenantId, customerId, amount, occurredAt, CURRENT_SCHEMA_VERSION);
  }
}
