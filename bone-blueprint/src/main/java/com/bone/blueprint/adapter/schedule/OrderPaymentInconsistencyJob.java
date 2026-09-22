package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.port.out.OrderOutboxPort;
import com.bone.blueprint.domain.model.order.event.OrderPaymentInconsistentEvent;
import com.bone.blueprint.domain.model.order.valueobject.OrderStatus;
import com.bone.blueprint.domain.model.payment.projection.PaymentProjection;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「钱货不一致」对账扫描（全租户）：支付单已 SUCCESS，但订单在宽限期后仍未确认支付。
 *
 * <p><b>为何需要</b>：支付成功事实已与支付单同事务落 Outbox（P-5.4），但<strong>订单确认</strong>仍由 AFTER_COMMIT
 * 的进程内事件驱动——若订阅器执行失败或进程在提交后崩溃，就会出现"钱已收、货未付"。这类偏差不会自我修复： 支付单已是终态 SUCCESS、超时任务只扫
 * PENDING/PAYING，若不主动对账将永久沉没。
 *
 * <p><b>只告警、不自动改单</b>：资金状态的人工/自动补偿需业务决策（退款或补单），自动改单会掩盖真实资金问题； 本任务的责任是让异常<strong>可观测</strong>（error
 * 日志 + 计数），真实环境应接告警/工单。
 *
 * <p><b>跨上下文不 JOIN</b>：按 E-1.3，支付与订单是两个上下文，故先查支付单再按 ID 查订单状态，不在 SQL 层跨上下文联表。
 *
 * <p><b>两侧取数为何不对称</b>：支付侧扫描是全租户运维入口（{@link
 * PaymentRepository#findSuccessPaymentsBeforeAllTenants}），按 ADR-0030 §2 由定时 Job
 * 直接调域仓储；订单侧只是<strong>逐行按 ID 取状态</strong>、属于请求级读语义，故经 {@link
 * OrderApplicationService#findOrderStatus} 走应用层。 判据是「这次读是不是平台运维旁路」，
 * 不是「它读的是哪个聚合」——不要据此把订单侧也改成直连域仓储（那会绕开应用层的租户显式化）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentInconsistencyJob {

  private final PaymentRepository paymentRepository;
  private final OrderApplicationService orderApplicationService;
  private final OrderOutboxPort orderOutboxWriter;

  /**
   * 宽限期（分钟）：支付成功后允许订单确认的最大滞后，超过即视为不一致。
   *
   * <p><b>为何可配置</b>：宽限期就是<strong>容忍度</strong>——放宽减少误报、放窄更快发现偏差，属运营取舍；且它直接 决定对账门限，硬编码会迫使每次调整都走发版。
   */
  @Value("${bone.blueprint.schedule.confirm-grace-minutes:10}")
  private long confirmGraceMinutes;

  /**
   * 全租户对账：支付单已 SUCCESS，但订单在宽限期后仍未确认支付。
   *
   * <p><b>可观测性闭环</b>：事件驱动路径（{@code PaymentSucceededEventHandler}）在回调时已把偏差落 Outbox，
   * 但本任务是<strong>进程崩溃 / 订阅失败</strong>场景的安全网——这类偏差事件驱动路径根本不会发生。 因此本任务在检出偏差时，除 error
   * 日志外，<strong>也同事务落 Outbox</strong>（与事件驱动路径共用同一告警 / 工单 / 自动退款消费链路），使安全网发现同样可持久观测，而非仅沉没在日志中。
   *
   * <p><b>为何 {@code @Transactional}</b>：{@code OrderOutboxPort.appendPaymentInconsistent} 声明 {@code
   * Propagation.MANDATORY}，必须在事务内调用；整段扫描（读 + 落 Outbox）同事务，保证「读到的偏差」与「待发事件」一致。
   *
   * <p><b>重复告警是可接受的</b>：未修复的偏差每轮扫描都会重新落 Outbox（eventId 每次新生），下游按 paymentId
   * 去重即可——这恰恰是把「仍未解决」持续推给告警/工单的正确行为。
   */
  @Scheduled(cron = "${bone.blueprint.schedule.payment-inconsistency-check-cron:0 0/10 * * * ?}")
  @Transactional
  public void checkPaidButOrderNotConfirmed() {
    Instant before = Instant.now().minusSeconds(confirmGraceMinutes * 60);
    List<PaymentProjection> succeeded =
        paymentRepository.findSuccessPaymentsBeforeAllTenants(before);
    if (succeeded.isEmpty()) {
      log.info("[全租户对账] 无待对账支付单（宽限={}min）", confirmGraceMinutes);
      return;
    }

    // 按租户分组批量取订单状态（一次 IN 查询/租户），避免逐行查库的 N+1（设计说明见类头注释：
    // 订单侧读仍走应用层以保留租户显式化，故批量取也经 OrderApplicationService）。
    Map<Long, Long> orderIdToTenantId =
        succeeded.stream()
            .collect(
                Collectors.toMap(
                    PaymentProjection::getOrderId, PaymentProjection::getTenantId, (a, b) -> a));
    Map<Long, OrderStatus> statuses = orderApplicationService.findOrderStatuses(orderIdToTenantId);

    int inconsistent = 0;
    for (PaymentProjection row : succeeded) {
      OrderStatus status = statuses.get(row.getOrderId());
      // 订单不存在（Map 中无该 id）：同样属异常（支付成功却没有订单）；仍 CREATED：确认链路未执行
      if (status == null || status == OrderStatus.CREATED) {
        inconsistent++;
        String orderStatus = status == null ? "NOT_FOUND" : status.name();
        log.error(
            "钱货不一致：支付单已成功但订单未确认支付，需人工/自动补偿: paymentId={}, orderId={}, tenantId={}, "
                + "orderStatus={}, paidAt={}",
            row.getPaymentId(),
            row.getOrderId(),
            row.getTenantId(),
            orderStatus,
            row.getPaidAt());
        // 安全网偏差同事务落 Outbox，接入统一告警/工单/自动退款链路（与事件驱动路径共用）。
        // 定时线程没有请求上下文，而 Outbox 落库走 SDK 写路径（save 内部先按主键+租户探测存在性）：
        // 不显式声明租户就会被 ADR-0029 失败关闭拦下，本轮的偏差事件全部丢失。
        TenantContextRunner.runAs(
            row.getTenantId(),
            () ->
                orderOutboxWriter.appendPaymentInconsistent(
                    new OrderPaymentInconsistentEvent(
                        row.getOrderId(),
                        row.getTenantId(),
                        row.getPaymentId(),
                        orderStatus,
                        "对账扫描：支付成功但订单未确认支付（订单不存在或仍为待支付）",
                        Instant.now())));
      }
    }

    log.info(
        "[全租户对账] 支付成功订单确认对账完成: 扫描={} 笔, 不一致={} 笔（宽限={}min）",
        succeeded.size(),
        inconsistent,
        confirmGraceMinutes);
  }
}
