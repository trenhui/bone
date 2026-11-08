package com.bone.claim.adapter.schedule;

import com.bone.claim.application.SlaApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * SLA / limit_hour 物化定时任务（§6.6，ADR-0030 平台运维旁路）。
 *
 * <p>镜像 {@code CloseExpiredClaimJob}/{@code OutboxRelayJob}：全租户扫描未终结案件 {@link
 * ClaimCaseRepository#findActiveClaimsAllTenants}（{@code @TenantScope(ALL)} 方法），逐笔 {@link
 * TenantContextRunner#runAs} 显式携带租户调 {@link SlaApplicationService#refreshForClaim}（从 track 物化 SLA
 * 读模型）。
 *
 * <p><b>为何不写 {@code t_claim_case.limit_hour}</b>：见 {@link SlaApplicationService} 类注释——避免每 20 分钟刷新污染
 * {@code updated_at} 致超期关单扫描失效；{@code limit_hour} 口径由 {@code t_claim_case_sla.remaining_work_hours}
 * 承载。
 *
 * <p><b>E-2 显式租户</b>：扫描行租户必须显式带入刷新动作（异步分支不依赖线程上下文），否则非平台租户案件被静默漏算。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshLimitHourJob {

  private final ClaimCaseRepository claimCaseRepository;
  private final SlaApplicationService slaApplicationService;

  /** 单批扫描上限。 */
  @Value("${bone.claim.schedule.sla-batch:500}")
  private int batch;

  /** 周期可配置（默认每 20 分钟）。 */
  @Scheduled(cron = "${bone.claim.schedule.refresh-limit-hour-cron:0 */20 * * * ?}")
  public void refreshLimitHour() {
    List<ClaimTenantRef> active = claimCaseRepository.findActiveClaimsAllTenants(batch);
    int refreshed = 0;
    int failed = 0;
    for (ClaimTenantRef ref : active) {
      try {
        TenantContextRunner.runAs(
            ref.tenantId(), () -> slaApplicationService.refreshForClaim(ref.claimId()));
        refreshed++;
      } catch (Exception e) {
        failed++;
        log.error("SLA 物化失败: claimId={}, tenantId={}", ref.claimId(), ref.tenantId(), e);
      }
    }

    // 区分「命中」与「实际完成」：只打命中数会掩盖物化失败。
    log.info(
        "[全租户扫描] SLA/limit_hour 物化完成: 批上限={}, 命中={}, 成功={}, 失败={}（E-2 平台运维入口）",
        batch,
        active.size(),
        refreshed,
        failed);
  }
}
