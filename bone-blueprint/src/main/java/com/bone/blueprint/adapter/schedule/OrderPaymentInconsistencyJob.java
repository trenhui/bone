package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.query.dto.PaymentRow;
import com.bone.blueprint.application.query.port.OrderReadPort;
import com.bone.blueprint.application.query.port.PaymentReadPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
 * <p><b>跨上下文不 JOIN</b>：按 E-4.1.1，支付与订单是两个上下文，故先查支付单再按 ID 查订单状态，不在 SQL 层跨上下文联表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaymentInconsistencyJob {

  private final PaymentReadPort paymentReadPort;
  private final OrderReadPort orderReadPort;

  /** 宽限期（分钟）：支付成功后允许订单确认的最大滞后，超过即视为不一致。 */
  private static final long CONFIRM_GRACE_MINUTES = 10;

  @Scheduled(cron = "0 0/10 * * * ?")
  public void checkPaidButOrderNotConfirmed() {
    Instant before = Instant.now().minusSeconds(CONFIRM_GRACE_MINUTES * 60);
    List<PaymentRow> succeeded = paymentReadPort.findSuccessCreatedBeforeAllTenants(before);

    int inconsistent = 0;
    for (PaymentRow row : succeeded) {
      Optional<String> status = orderReadPort.findStatusById(row.getTenantId(), row.getOrderId());
      // 订单不存在：同样属异常（支付成功却没有订单）；仍 CREATED：确认链路未执行
      if (status.isEmpty() || status.get().equals("CREATED")) {
        inconsistent++;
        log.error(
            "钱货不一致：支付单已成功但订单未确认支付，需人工/自动补偿: paymentId={}, orderId={}, tenantId={}, "
                + "orderStatus={}, paidAt={}",
            row.getPaymentId(),
            row.getOrderId(),
            row.getTenantId(),
            status.orElse("NOT_FOUND"),
            row.getPaidAt());
      }
    }

    log.info(
        "[全租户对账] 支付成功订单确认对账完成: 扫描={} 笔, 不一致={} 笔（宽限={}min）",
        succeeded.size(),
        inconsistent,
        CONFIRM_GRACE_MINUTES);
  }
}
