package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.integration.event.IntegrationEnvelope;
import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.integration.event.OrderPaymentInconsistentIntegrationEvent;
import com.bone.blueprint.application.integration.event.OrderStockActionFailedIntegrationEvent;
import com.bone.blueprint.application.integration.event.PaymentFailedIntegrationEvent;
import com.bone.blueprint.application.integration.event.PaymentRefundedIntegrationEvent;
import com.bone.blueprint.application.integration.event.PaymentSucceededIntegrationEvent;
import com.bone.blueprint.application.port.out.OrderOutboxPort;
import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;
import com.bone.blueprint.infrastructure.config.OrderOutboxProperties;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.query.criteria.Criteria;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link OrderOutboxPort} 的基础设施实现：在业务事务内写入 Outbox 表。
 *
 * <p><b>端口在 application/port/out、实现在 infrastructure</b>：E-10 出站端口声明于
 * application/port/out，application 依赖领域接口、 不依赖 infrastructure（P0-1 依赖方向），同时 Outbox
 * 这一消息投递机制不侵入领域业务类型——三方职责因此分明。 载荷先由本实现把领域事件转换为跨边界集成事件（ACL 职责），再序列化为信封。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxPortAdapter implements OrderOutboxPort {

  private static final String EVENT_TYPE_ORDER_PAID = "OrderPaidIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_INCONSISTENT =
      "OrderPaymentInconsistentIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_SUCCEEDED = "PaymentSucceededIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_REFUNDED = "PaymentRefundedIntegrationEvent";
  private static final String EVENT_TYPE_PAYMENT_FAILED = "PaymentFailedIntegrationEvent";
  private static final String EVENT_TYPE_STOCK_ACTION_FAILED =
      "OrderStockActionFailedIntegrationEvent";

  private final OrderOutboxProperties properties;
  private final OrderOutboxRepository outboxRepository;
  private final OrderOutboxEnvelopeFactory envelopeFactory;
  private final TenantPort tenantProvider;

  /**
   * 支付成功事实入 Outbox（MANDATORY：强制调用方已有事务）。
   *
   * <p><b>为何用 MANDATORY</b>：「事件与业务状态同事务」是 Outbox 的唯一价值来源。用默认 REQUIRED 时，
   * 若调用方没有事务，容器会<strong>悄悄新起一个事务</strong>，原子性被破坏却不报错。MANDATORY 把这条 约束从注释升级为容器级保证：无事务调用直接抛
   * IllegalTransactionStateException。
   */
  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentSucceeded(PaymentSucceededEvent event) {
    append(
        EVENT_TYPE_PAYMENT_SUCCEEDED,
        properties.getPaymentSucceededTopic(),
        event == null
            ? null
            : PaymentSucceededIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.amount(),
                event.channelTradeNo(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendOrderPaid(OrderPaidEvent event) {
    append(
        EVENT_TYPE_ORDER_PAID,
        properties.getOrderPaidTopic(),
        event == null
            ? null
            : OrderPaidIntegrationEvent.fromDomain(
                event.orderId(),
                event.tenantId(),
                event.customerId(),
                event.amount(),
                event.occurredAt()),
        event == null ? null : event.orderId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentInconsistent(OrderPaymentInconsistentEvent event) {
    append(
        EVENT_TYPE_PAYMENT_INCONSISTENT,
        properties.getPaymentInconsistentTopic(),
        event == null
            ? null
            : OrderPaymentInconsistentIntegrationEvent.fromDomain(
                event.orderId(),
                event.tenantId(),
                event.paymentId(),
                event.orderStatus(),
                event.reason(),
                event.occurredAt()),
        event == null ? null : event.orderId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentRefunded(PaymentRefundedEvent event) {
    append(
        EVENT_TYPE_PAYMENT_REFUNDED,
        properties.getPaymentRefundedTopic(),
        event == null
            ? null
            : PaymentRefundedIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.refundAmount(),
                event.channelTradeNo(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendPaymentFailed(PaymentFailedEvent event) {
    append(
        EVENT_TYPE_PAYMENT_FAILED,
        properties.getPaymentFailedTopic(),
        event == null
            ? null
            : PaymentFailedIntegrationEvent.fromDomain(
                event.paymentId(),
                event.tenantId(),
                event.orderId(),
                event.amount(),
                event.occurredAt()),
        event == null ? null : event.paymentId());
  }

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendStockActionFailed(OrderStockActionFailedIntegrationEvent event) {
    // 幂等键：同一（订单 × 商品 × 库存动作）只落一条失败事实。
    //
    // 为何需要：本事件是「补偿触发器」而非事件流——它表达「这个订单行的这次库存动作没做成」这一状态，
    // 重放/重试下重复投递会让下游重复补偿（重复释放预留、重复告警）。故用业务身份派生确定性 eventId：
    // 同一事实无论重试几次都得到同一个 eventId，写侧先查后插即可天然去重，消费端按 eventId 去重也同时生效。
    append(
        EVENT_TYPE_STOCK_ACTION_FAILED,
        properties.getStockActionFailedTopic(),
        event,
        event == null ? null : event.orderId(),
        event == null ? null : stockActionFailedEventId(event));
  }

  /** 库存动作失败事实的确定性事件ID（幂等键）：订单 × 商品 × 动作三元组。 */
  private static String stockActionFailedEventId(OrderStockActionFailedIntegrationEvent event) {
    return EVENT_TYPE_STOCK_ACTION_FAILED
        + "-"
        + event.tenantId()
        + "-"
        + event.orderId()
        + "-"
        + event.productId()
        + "-"
        + event.actionName();
  }

  private void append(String eventType, String topic, Object event, Long bizId) {
    append(eventType, topic, event, bizId, null);
  }

  /**
   * 写入一条 PENDING 记录。
   *
   * <p><b>为何与业务写同事务</b>：Outbox 模式的全部价值就在「业务状态变更」与「事件待发」的原子性—— 二者同事务提交，才不会出现「业务成功而事件丢失」或「事件已发而业务回滚」。
   *
   * <p><b>{@code dedupEventId}</b>：非空时用它作为 eventId（确定性幂等键），并在插入前先查一次——已存在则跳过。
   * 仅对「补偿触发器」形态的事件（如库存动作失败）启用；事实流类事件仍用随机 eventId。
   */
  private void append(
      String eventType, String topic, Object event, Long bizId, String dedupEventId) {
    if (event == null) {
      return;
    }
    if (!properties.isEnabled()) {
      // 关闭 Outbox 等于声明「这些事件可容忍丢失」，必须留痕：静默 return 会让资金/状态事实凭空消失且无人察觉。
      log.warn(
          "Outbox 已关闭，集成事件未落库（下游将收不到该事实）: eventType={}, bizId={}。"
              + "若为生产环境请检查 bone.blueprint.outbox.enabled",
          eventType,
          bizId);
      return;
    }
    String eventId = dedupEventId != null ? dedupEventId : envelopeFactory.newEventId();
    if (dedupEventId != null && alreadyAppended(eventId)) {
      log.debug(
          "Outbox 幂等跳过：同一事实已落库，不重复补偿: eventId={}, eventType={}, bizId={}",
          eventId,
          eventType,
          bizId);
      return;
    }
    long tenantId = resolveTenantId(event);
    OrderOutboxRecord record =
        OrderOutboxRecord.pending(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            eventId,
            eventType,
            topic,
            String.valueOf(tenantId),
            envelopeFactory.toJson(
                eventId, eventType, topic, tenantId, resolveOccurredAt(event), event));
    outboxRepository.save(record);
    log.debug("Outbox 已写入: eventId={}, eventType={}, bizId={}", eventId, eventType, bizId);
  }

  /**
   * 幂等前置检查：该 eventId 是否已落库。
   *
   * <p><b>为何不依赖数据库唯一约束</b>：Outbox 表无 {@code event_id} 唯一索引（历史表结构，加索引需 DDL 迁移），
   * 故在事务内「先查后插」。并发窗口下仍有极小概率产生重复行，但本路径是<strong>单写者</strong>（AFTER_COMMIT 处理器按订单行串行执行），且下游按 eventId
   * 去重才是最终兜底——这里消掉的是绝大多数「重放/重试」重复。
   */
  private boolean alreadyAppended(String eventId) {
    Long count =
        outboxRepository.countByCriteria(
            Criteria.<OrderOutboxRecord>create().eq(OrderOutboxRecord::getEventId, eventId));
    return count != null && count > 0;
  }

  /**
   * 事实发生时间：优先取集成事件自带时间（= 领域事件注册时刻），供下游排序与对账。
   *
   * <p>取不到时回退为落库时刻——二者在同一事务内，差异仅为事务内的处理耗时。
   */
  private Instant resolveOccurredAt(Object event) {
    if (event instanceof IntegrationEnvelope e) {
      return e.occurredAt();
    }
    return Instant.now();
  }

  /** 事件自带租户则优先用事件携带值（避免跨租户误写），否则回落当前上下文租户。 */
  private long resolveTenantId(Object event) {
    if (event instanceof IntegrationEnvelope e && e.tenantId() != null) {
      return e.tenantId();
    }
    return tenantProvider.currentTenantId();
  }
}
