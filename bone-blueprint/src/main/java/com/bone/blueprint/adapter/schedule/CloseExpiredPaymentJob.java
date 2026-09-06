package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.command.cmd.CloseExpiredPaymentCommand;
import com.bone.blueprint.application.command.handler.CloseExpiredPaymentCommandHandler;
import com.bone.blueprint.domain.gateway.PaymentReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.read.PaymentRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 关闭超时未支付支付单定时任务。
 *
 * <p>定时扫描仍处于 PENDING/PAYING 且创建时间早于超时阈值的支付单，逐笔下发 {@link CloseExpiredPaymentCommand}（**经应用层 Handler
 * 执行，adapter 不直连 domain 仓储**——与 {@code CancelExpiredOrderJob} 同模式，§15）。
 *
 * <p><b>平台租户打洞（E-4.4 登记）</b>：定时任务线程无请求上下文，取数统一降级为平台租户（{@code tenantId=0}，经 {@code TenantProvider}
 * 端口，由 {@code TenantProviderAdapter} 留审计日志），属本模块 README「多租户 · 平台租户打洞登记」登记项。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredPaymentJob {

  private final PaymentReadPort paymentReadPort;
  private final TenantProvider tenantProvider;
  private final CloseExpiredPaymentCommandHandler closeExpiredPaymentCommandHandler;

  /** 超时阈值（分钟）：支付单创建后超过该时长未支付即关闭。 */
  private static final long PAYMENT_TIMEOUT_MINUTES = 30;

  @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行一次
  public void closeExpiredPayments() {
    long tenantId = tenantProvider.currentTenantId();
    Instant before = Instant.now().minusSeconds(PAYMENT_TIMEOUT_MINUTES * 60);
    log.info(
        "[打洞] 定时任务以租户 tenantId={} 扫描超时支付单（阈值={}min，E-4.4 打洞登记）", tenantId, PAYMENT_TIMEOUT_MINUTES);
    List<PaymentRow> expired = paymentReadPort.findPayableExpiredBefore(tenantId, before);
    if (expired.isEmpty()) {
      return;
    }
    log.info("发现超时未支付支付单: {} 个", expired.size());
    for (PaymentRow row : expired) {
      try {
        closeExpiredPaymentCommandHandler.handle(
            new CloseExpiredPaymentCommand(row.getPaymentId()));
      } catch (Exception e) {
        // 记录日志，继续处理下一笔（如状态已迁移导致 close 抛错，属预期跳过）
        log.error("关闭超时支付单失败: paymentId={}", row.getPaymentId(), e);
      }
    }
  }
}
