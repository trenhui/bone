package com.bone.blueprint.domain.gateway;

import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;

/**
 * 集成事件 Outbox（发件箱）写入端口——<strong>出站端口（E-10），声明于 domain，实现于基础设施</strong>。
 *
 * <p><b>为何端口在 domain 而非 application</b>：E-10 强制「出站端口在 domain」（如 {@code PaymentGateway}、 {@code
 * InventoryGateway}），application 依赖领域接口、不依赖基础设施实现，保持依赖方向向内。载荷使用
 * <strong>领域事件类型</strong>（E-10「入参/出参均为领域类型」），由基础设施实现（{@code OrderOutboxWriterImpl}） 转换为跨边界集成事件（ACL
 * 职责）。
 *
 * <p><b>为何实现在 infrastructure 而非 domain</b>：Outbox 记录只有 PENDING/SENT/FAILED 的<strong>技术
 * 状态</strong>，无业务不变量。放领域层会把技术设施概念混入业务领域（原 {@code domain/outbox} 已移除）。
 *
 * <p><b>调用约束（关键）</b>：必须在业务写事务<strong>内</strong>调用，保证「业务状态变更」与「事件待发」 原子提交。若挪到
 * AFTER_COMMIT，业务已提交而事件未落库，宕机即丢事件——Outbox 模式将完全失效（P-5.4）。
 */
public interface OrderOutboxWriter {

  /**
   * 记录「支付已成功」集成事件（<b>须与支付单确认成功同事务</b>）。
   *
   * <p>这是 Outbox 模式的关键落点：资金事实不可容忍丢失（P-5.4），必须先与业务数据同事务落库， 不能依赖 AFTER_COMMIT
   * 的进程内事件——崩溃窗口内事件会永久消失且无迹可寻。
   */
  void appendPaymentSucceeded(PaymentSucceededEvent event);

  /** 记录「订单已支付」集成事件（须与订单确认支付同事务）。 */
  void appendOrderPaid(OrderPaidEvent event);

  /**
   * 记录「钱货不一致待补偿」集成事件（须与异常判定同事务）。
   *
   * <p>支付已成功而订单无法确认支付时调用，使异常可观测、可补偿，避免资金与订单状态静默偏离。
   */
  void appendPaymentInconsistent(OrderPaymentInconsistentEvent event);
}
