package com.bone.claim.adapter.schedule;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超期未补件关单定时任务（ADR-0030 平台运维旁路，对应领域 {@code ClaimCase.closeForStale}：{@code EVIDENCE_SUBMITTING}
 * 态停留超过阈值自动关单为 {@code CLOSED_REJECTED}，§6.6 SUBMITTING 超期关单）。
 *
 * <p><b>本类为何直连域仓储</b>：扫描入口是全租户方法 {@link ClaimCaseRepository#findStaleEvidenceClaimsAllTenants}， 与
 * blueprint {@code CloseExpiredPaymentJob} 完全同形——定时线程无请求上下文，按「当前租户」扫描会退化为平台租户 0。ADR-0030 §2
 * 把这类<strong>平台运维旁路</strong>的调用方明确写为定时 Job；本类受 {@code all_tenants_scan_only_by_schedule} 与 {@code
 * schedule_only_calls_all_tenants_repository_methods} 双重约束（schedule 包内对域仓储只许调 {@code *AllTenants}
 * 方法）。<strong>其余入站适配器（web / rpc / messaging）不得复制此形态</strong>。
 *
 * <p><b>全租户扫描 + 显式租户（E-2）</b>：扫描行的 {@code tenantId} 必须<strong>显式携带</strong>进关闭动作（异步分支不依赖线程上下文）；
 * 否则除平台租户外的赔案会静默漏掉，而日志仍显示「扫描完成」。写路径 ADR-0029 对租户表失败关闭，必须用 {@link TenantContextRunner#runAs}
 * 显式声明租户，否则 {@code MissingTenantContextException} 被下方 catch 静默吞掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloseExpiredClaimJob {

  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimCaseApplicationService claimCaseApplicationService;

  /**
   * 超期阈值（天）：{@code EVIDENCE_SUBMITTING} 状态停留超过该时长即自动关单（领域标注 21→45，默认 45d）。
   *
   * <p><b>为何可配置</b>：超时窗口是<strong>运营策略</strong>（各客户补件时限不同），硬编码意味着每次调整都要改代码、走一次发版；
   * 本任务的行为完全由该阈值决定。默认值取领域约定 45d，与 §6.6 的 30d 提示冲突时以配置为准。
   */
  @Value("${bone.claim.schedule.stale-evidence-days:45}")
  private long staleEvidenceDays;

  /** 系统操作人（无人类操作者的自动关单动作）。 */
  private static final String SYSTEM_OPERATOR = "SCHEDULER_CLOSE_STALE";

  /** 扫描周期可配置（默认每日 03:00 扫一次）。 */
  @Scheduled(cron = "${bone.claim.schedule.close-stale-claims-cron:0 0 3 * * ?}")
  public void closeStaleClaims() {
    Timestamp before = Timestamp.from(Instant.now().minus(staleEvidenceDays, ChronoUnit.DAYS));
    List<ClaimTenantRef> stale = claimCaseRepository.findStaleEvidenceClaimsAllTenants(before);
    int closed = 0;
    int failed = 0;
    for (ClaimTenantRef ref : stale) {
      try {
        // 调度线程无请求上下文：写路径 ADR-0029 失败关闭，必须 runAs 显式声明租户，否则静默漏单。
        TenantContextRunner.runAs(
            ref.tenantId(),
            () -> claimCaseApplicationService.closeForStale(ref.claimId(), SYSTEM_OPERATOR));
        closed++;
      } catch (Exception e) {
        failed++;
        // 记录日志，继续处理下一笔（如状态已迁移导致 close 抛错，属预期跳过）
        log.error("超期关单失败: claimId={}, tenantId={}", ref.claimId(), ref.tenantId(), e);
      }
    }

    // 必须区分「命中」与「实际完成」：只打命中数时，「命中 10 笔全部失败」与「全部成功」在日志上完全同形。
    log.info(
        "[全租户扫描] 超期未补件关单完成: 阈值={}d, 命中={} 笔, 成功={}, 失败={}（E-2 平台运维入口）",
        staleEvidenceDays,
        stale.size(),
        closed,
        failed);
  }
}
