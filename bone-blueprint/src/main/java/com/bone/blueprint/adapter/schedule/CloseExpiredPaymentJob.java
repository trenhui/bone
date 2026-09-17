package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.command.cmd.CloseExpiredPaymentCommand;
import com.bone.blueprint.application.command.handler.CloseExpiredPaymentCommandHandler;
import com.bone.blueprint.application.query.dto.PaymentProjection;
import com.bone.blueprint.application.query.port.PaymentQueryPort;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 关闭超时未支付支付单定时任务。
 *
 * <p>定时扫描仍处于 PENDING/PAYING 且创建时间早于超时阈值的支付单，逐笔下发 {@link CloseExpiredPaymentCommand} （经应用层 Handler
 * 执行，adapter 不直连 domain 仓储）。
 *
 * <p><b>全租户扫描（E-4.4）</b>：与 {@link CancelExpiredOrderJob} 同理——定时线程无请求上下文，按"当前租户"扫描 只会落到降级后的平台租户
 * 0，导致除平台租户外的超时支付单永不关闭。改为全租户读端口 + 命令显式携带租户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredPaymentJob {

  private final PaymentQueryPort paymentQueryPort;
  private final CloseExpiredPaymentCommandHandler closeExpiredPaymentCommandHandler;

  /** 超时阈值（分钟）：支付单创建后超过该时长未支付即关闭。 */
  private static final long PAYMENT_TIMEOUT_MINUTES = 30;

  @Scheduled(cron = "0 0/5 * * * ?")
  public void closeExpiredPayments() {
    Instant before = Instant.now().minusSeconds(PAYMENT_TIMEOUT_MINUTES * 60);
    List<PaymentProjection> expired = paymentQueryPort.findPayableExpiredBeforeAllTenants(before);
    log.info(
        "[全租户扫描] 超时支付单扫描完成: 阈值={}min, 命中={} 笔（E-4.4 平台运维入口，README 已登记）",
        PAYMENT_TIMEOUT_MINUTES,
        expired.size());
    for (PaymentProjection row : expired) {
      try {
        closeExpiredPaymentCommandHandler.handle(
            new CloseExpiredPaymentCommand(row.getPaymentId(), row.getTenantId()));
      } catch (Exception e) {
        // 记录日志，继续处理下一笔（如状态已迁移导致 close 抛错，属预期跳过）
        log.error("关闭超时支付单失败: paymentId={}, tenantId={}", row.getPaymentId(), row.getTenantId(), e);
      }
    }
  }
}
