package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.domain.gateway.PaymentReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
import com.bone.blueprint.domain.payment.Payment;
import com.bone.blueprint.domain.payment.read.PaymentRow;
import com.bone.blueprint.domain.repository.PaymentRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 关闭超时未支付支付单定时任务。
 *
 * <p>定时扫描仍处于 PENDING/PAYING 且创建时间早于超时阈值的支付单，逐一关闭（复用聚合 {@code Payment.close()}
 * 状态机）。示范真实支付域「超时未支付自动关闭」生命周期闭环。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredPaymentJob {

  private final PaymentReadPort paymentReadPort;
  private final PaymentRepository paymentRepository;
  private final TenantProvider tenantProvider;

  /** 超时阈值（分钟）：支付单创建后超过该时长未支付即关闭。 */
  private static final long PAYMENT_TIMEOUT_MINUTES = 30;

  @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行一次
  public void closeExpiredPayments() {
    long tenantId = tenantProvider.currentTenantId();
    Instant before = Instant.now().minusSeconds(PAYMENT_TIMEOUT_MINUTES * 60);
    List<PaymentRow> expired = paymentReadPort.findPayableExpiredBefore(tenantId, before);
    if (expired.isEmpty()) {
      return;
    }
    log.info("发现超时未支付支付单: {} 个", expired.size());
    for (PaymentRow row : expired) {
      closeOne(tenantId, row.getPaymentId());
    }
  }

  @Transactional
  public void closeOne(long tenantId, long paymentId) {
    Payment payment = paymentRepository.findByIdInTenant(paymentId, tenantId);
    if (payment == null) {
      log.warn("支付单不存在或非本租户: paymentId={}", paymentId);
      return;
    }
    try {
      payment.close();
      paymentRepository.save(payment);
      log.info("已关闭超时支付单: paymentId={}", paymentId);
    } catch (Exception e) {
      log.error("关闭超时支付单失败: paymentId={}", paymentId, e);
    }
  }
}
