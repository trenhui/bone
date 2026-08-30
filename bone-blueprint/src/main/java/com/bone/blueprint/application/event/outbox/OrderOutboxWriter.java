package com.bone.blueprint.application.event.outbox;

import com.bone.blueprint.application.integration.event.OrderPaidIntegrationEvent;
import com.bone.blueprint.application.integration.event.OrderPaymentInconsistentIntegrationEvent;

/**
 * Outbox（发件箱）写入端口——<strong>应用层定义，基础设施层实现</strong>。
 *
 * <p><b>为何端口在 application 而非 domain</b>：Outbox 解决的是「业务事务与消息投递之间的一致性」， 属应用层的集成职责；领域层不应感知消息中间件的存在。实现见
 * {@code infrastructure/messaging/outbox/OrderOutboxWriterImpl}。
 *
 * <p><b>为何实现在 infrastructure 而非 domain</b>：Outbox 记录只有 PENDING/SENT/FAILED 的<strong>技术
 * 状态</strong>，无业务不变量。放领域层会把技术设施概念混入业务领域（原 {@code domain/outbox} 已移除）。
 *
 * <p><b>调用约束（关键）</b>：必须在业务写事务<strong>内</strong>调用，保证「业务状态变更」与「事件待发」 原子提交。若挪到
 * AFTER_COMMIT，业务已提交而事件未落库，宕机即丢事件——Outbox 模式将完全失效。
 */
public interface OrderOutboxWriter {

  /** 记录「订单已支付」集成事件（须与订单确认支付同事务）。 */
  void appendOrderPaid(OrderPaidIntegrationEvent event);

  /**
   * 记录「钱货不一致待补偿」集成事件（须与异常判定同事务）。
   *
   * <p>支付已成功而订单无法确认支付时调用，使异常可观测、可补偿，避免资金与订单状态静默偏离。
   */
  void appendPaymentInconsistent(OrderPaymentInconsistentIntegrationEvent event);
}
