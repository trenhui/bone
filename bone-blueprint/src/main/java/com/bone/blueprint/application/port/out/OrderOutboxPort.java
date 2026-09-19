package com.bone.blueprint.application.port.out;

import com.bone.blueprint.domain.order.event.OrderPaidEvent;
import com.bone.blueprint.domain.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.payment.event.PaymentFailedEvent;
import com.bone.blueprint.domain.payment.event.PaymentRefundedEvent;
import com.bone.blueprint.domain.payment.event.PaymentSucceededEvent;

/**
 * 集成事件 Outbox（发件箱）写入端口——<strong>技术出站端口（E-4.3 / E-10.2），声明于 {@code application/port/out}，实现于
 * infrastructure</strong>。
 *
 * <p><b>为何端口在 application/port/out 而非 domain/gateway</b>：E-4.3 明确「领域规则直接需要、且能用本上下文业务语言 表达的外部业务能力」才是
 * Domain Gateway；判据是「拿掉该外部系统，业务规则本身仍能表达，就不是 Domain Gateway」。 Outbox 写是<strong>技术写</strong>（发
 * MQ、记待发事件），不属于任何业务不变量，因此与发短信、发 MQ、写缓存、取当前时间 等同属<strong>技术能力</strong>，一律不得进 {@code
 * domain/gateway}（E-4.3 列表）；{@code OrderMessagePort}、 {@code OrderOutboxRelayPort}、{@code
 * TenantPort} 同理。E-10.2 决策树亦将其归入「应用流程需要的技术能力 → application/port/out」。{@code PaymentGateway}/{@code
 * InventoryGateway} 才是合法的 Domain Gateway（账户余额、 库存这类业务规则依赖的外部事实）。
 *
 * <p><b>为何实现在 infrastructure</b>：Outbox 记录只有 PENDING/SENT/FAILED 的<strong>技术状态</strong>，无业务不变量；
 * 基础设施实现（{@code OrderOutboxPortAdapter}）负责把领域事件转换为跨边界集成事件（ACL 职责），领域层不感知协议细节。
 *
 * <p><b>调用约束（关键）</b>：必须在业务写事务<strong>内</strong>调用，保证「业务状态变更」与「事件待发」 原子提交（E-5.1「Outbox
 * 等技术写必须与业务状态满足所声明的原子性」）。若挪到 AFTER_COMMIT，业务已提交而事件未落库，宕机即丢事件—— Outbox 模式将完全失效（P-5.4）。
 */
public interface OrderOutboxPort {

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

  /** 记录「支付已退款」集成事件（须与退款确认同事务）。 */
  void appendPaymentRefunded(PaymentRefundedEvent event);

  /**
   * 记录「支付已失败」集成事件（须与支付单置 FAILED 同事务）。
   *
   * <p>与退款/成功相比，失败不直接涉及资金流动，但下游（通知用户、告警监控）仍需感知。
   */
  void appendPaymentFailed(PaymentFailedEvent event);
}
