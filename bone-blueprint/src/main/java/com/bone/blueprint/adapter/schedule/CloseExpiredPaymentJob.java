package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.PaymentApplicationService;
import com.bone.blueprint.application.command.CloseExpiredPaymentCommand;
import com.bone.blueprint.domain.model.payment.projection.PaymentProjection;
import com.bone.blueprint.domain.repository.PaymentRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 关闭超时未支付支付单定时任务。
 *
 * <p>定时扫描仍处于 PENDING/PAYING 且创建时间早于超时阈值的支付单，逐笔下发 {@link CloseExpiredPaymentCommand}（经 {@link
 * PaymentApplicationService} 执行）。
 *
 * <p><b>本类为何直连域仓储</b>：扫描入口是全租户方法 {@link PaymentRepository#findExpiredPaymentsAllTenants}，与 {@link
 * CancelExpiredOrderJob} 完全同形——定时线程无请求上下文， 按「当前租户」扫描会退化为平台租户 0。ADR-0030 §2
 * 把这类<strong>平台运维旁路</strong>的调用方明确写为定时 Job；本类受 {@code all_tenants_scan_only_by_schedule} 与本模块
 * {@code ArchitectureTest} 双重约束。schedule 包内调用域仓储时，只许调全租户扫描方法（门禁从 SDK 声明点自动识别）
 * 方法）。<strong>其余入站适配器（web / rpc / messaging）不得复制此形态</strong>。
 *
 * <p><b>全租户扫描（E-2）</b>：改为全租户扫描后，扫描行的 {@code tenantId} 必须<strong>显式携带</strong>进命令（异步分支不依赖线程上下文）；
 * 否则除平台租户外的支付单会静默漏掉，而日志仍显示"扫描完成"。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredPaymentJob {

  private final PaymentRepository paymentRepository;
  private final PaymentApplicationService paymentApplicationService;

  /**
   * 超时阈值（分钟）：支付单创建后超过该时长未支付即关闭。
   *
   * <p><b>为何可配置</b>：超时窗口是<strong>运营策略</strong>（各渠道回调时限不同），硬编码为 {@code static final}
   * 意味着每次调整都要改代码、走一次发版，而本任务的行为完全由该阈值决定。
   */
  @Value("${bone.blueprint.schedule.payment-timeout-minutes:30}")
  private long paymentTimeoutMinutes;

  /** 扫描周期可配置（默认每 5 分钟扫一次）。 */
  @Scheduled(cron = "${bone.blueprint.schedule.close-expired-payments-cron:0 0/5 * * * ?}")
  public void closeExpiredPayments() {
    Instant before = Instant.now().minusSeconds(paymentTimeoutMinutes * 60);
    List<PaymentProjection> expired = paymentRepository.findExpiredPaymentsAllTenants(before);
    int closed = 0;
    int failed = 0;
    for (PaymentProjection row : expired) {
      try {
        // 调度线程无请求上下文：D2 起写路径改走 SDK update(entity)，租户由 TenantContext 提供，
        // 必须用 runAs 显式声明租户（ADR-0029 失败关闭），否则 MissingTenantContextException 被下方 catch 静默吞掉。
        TenantContextRunner.runAs(
            row.getTenantId(),
            () ->
                paymentApplicationService.closeExpired(
                    new CloseExpiredPaymentCommand(row.getPaymentId(), row.getTenantId())));
        closed++;
      } catch (Exception e) {
        failed++;
        // 记录日志，继续处理下一笔（如状态已迁移导致 close 抛错，属预期跳过）
        log.error("关闭超时支付单失败: paymentId={}, tenantId={}", row.getPaymentId(), row.getTenantId(), e);
      }
    }

    // 必须区分「命中」与「实际完成」：只打命中数时，「命中 10 笔全部失败」与「全部成功」在日志上完全同形。
    log.info(
        "[全租户扫描] 超时支付单关闭完成: 阈值={}min, 命中={} 笔, 成功={}, 失败={}（E-2 平台运维入口，README 已登记）",
        paymentTimeoutMinutes,
        expired.size(),
        closed,
        failed);
  }
}
