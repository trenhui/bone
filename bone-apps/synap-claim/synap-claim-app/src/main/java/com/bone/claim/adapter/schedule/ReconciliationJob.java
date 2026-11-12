package com.bone.claim.adapter.schedule;

import com.bone.claim.application.ReconciliationApplicationService;
import com.bone.claim.domain.model.reconciliation.ReconTenantRef;
import com.bone.claim.domain.repository.ReconciliationReportRepository;
import com.bone.claim.infrastructure.schedule.ScheduleLockService;
import com.bone.core.tenant.context.TenantContextRunner;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资金日终对账定时任务（§11.7，ADR-0030 平台运维旁路）。
 *
 * <p>全租户枚举需对账的租户 {@link ReconciliationReportRepository#findTenantIdsAllTenants}（额度账户 ∪ 结算单的
 * distinct tenant），逐租户 {@link TenantContextRunner#runAs} 显式携带租户调 {@link
 * ReconciliationApplicationService#reconcileForTenant}（QUOTA + PAYMENT 两通道）。
 *
 * <p><b>业务日口径</b>：默认对昨日（{@code Asia/Shanghai}，T+1 对账），可经 {@code biz-date-offset-days} 调整。 <b>E-2
 * 显式租户</b>：扫描行租户必须显式带入对账动作，否则非平台租户被静默漏对。
 *
 * <p><b>调度单写者（P1-2，2026-11-12）</b>：本任务经 {@link #reconciliationDaily} 由 {@link ScheduleLockService}
 * 包裹，多副本部署下仅一个实例执行对账，杜绝重复对账/重复出金。锁的获取与释放在服务层，业务逻辑本体见 {@link #reconcileDailyBody}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJob {

  /** 调度单写者锁名（P1-2）。 */
  public static final String LOCK_NAME = "schedule:reconciliation";

  private final ReconciliationReportRepository reportRepository;
  private final ReconciliationApplicationService reconciliationApplicationService;
  private final ScheduleLockService scheduleLockService;

  /** 对账业务日相对今日的偏移天数（默认 1 = 昨日）。 */
  @Value("${bone.claim.schedule.reconciliation-biz-date-offset-days:1}")
  private long bizDateOffsetDays;

  /** 周期可配置（默认每日 01:00）。 */
  @Scheduled(cron = "${bone.claim.schedule.reconciliation-cron:0 0 1 * * ?}")
  public void reconcileDaily() {
    scheduleLockService.execute(LOCK_NAME, this::reconcileDailyBody);
  }

  /** 对账业务逻辑本体（无锁；单测直接调用，锁由 {@link #reconcileDaily} 的调度单写者包裹）。 */
  void reconcileDailyBody() {
    LocalDate bizDate = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(bizDateOffsetDays);
    List<ReconTenantRef> tenants = reportRepository.findTenantIdsAllTenants();
    int done = 0;
    int failed = 0;
    for (ReconTenantRef ref : tenants) {
      try {
        TenantContextRunner.runAs(
            ref.tenantId(), () -> reconciliationApplicationService.reconcileForTenant(bizDate));
        done++;
      } catch (Exception e) {
        failed++;
        log.error("对账失败: tenantId={}, bizDate={}", ref.tenantId(), bizDate, e);
      }
    }

    // 区分「命中」与「实际完成」：只打命中数会掩盖对账失败。
    log.info(
        "[全租户扫描] 资金对账完成: bizDate={}, 命中租户={}, 成功={}, 失败={}（E-2 平台运维入口）",
        bizDate,
        tenants.size(),
        done,
        failed);
  }
}
