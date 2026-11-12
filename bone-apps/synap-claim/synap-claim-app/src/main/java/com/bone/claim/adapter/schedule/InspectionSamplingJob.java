package com.bone.claim.adapter.schedule;

import com.bone.claim.application.InspectionApplicationService;
import com.bone.claim.application.InspectionApplicationService.SampleOutcome;
import com.bone.claim.domain.model.claim.projection.ClaimTenantRef;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.infrastructure.schedule.ScheduleLockService;
import com.bone.core.tenant.context.TenantContextRunner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 后督抽检抽样任务（§7.6 / MVP「结案案件抽检 ≥5%」，ADR-0030 平台运维旁路，ADR-0034 全租户入口判据）。
 *
 * <p>镜像 {@code RefreshLimitHourJob}：全租户扫描已结案且<b>尚未抽检</b>的案件（{@link
 * ClaimCaseRepository#findFinishedForInspectionAllTenants}，SQL 内 {@code NOT EXISTS} 排除已抽检，避免头部饿死），
 * 逐笔 {@link TenantContextRunner#runAs} 显式携带租户调 {@link
 * InspectionApplicationService#sampleIfEligible}。
 *
 * <p><b>抽样口径</b>：自动抽样只覆盖 {@code FINISHED}（已支付，监管「结案案件抽检」口径）；拒赔案件的复核走人工 发起（#13 MANUAL）——拒赔有 48h
 * 申诉通道兜底，把自动样本额度耗在拒赔上会稀释已出金案件的监督密度。
 *
 * <p><b>比例</b>：{@code bone.claim.schedule.inspection-sampling-rate}（百分比，默认 5）；置 100 即 V1.0「自动结论
 * 100% AI 质检」口径。抽样用确定性哈希（{@link
 * com.bone.claim.domain.model.inspection.InspectionSamplingPolicy}），同批案件重复跑结果一致。
 *
 * <p><b>日志必须区分四种结果</b>：命中抽样/未命中/已抽检/不适用任一被合并，都会让「抽样没生效」看起来像「样本本来就少」。
 *
 * <p><b>调度单写者（P1-2，2026-11-12）</b>：本任务经 {@link #sampleDaily} 由 {@link ScheduleLockService} 包裹，
 * 多副本下仅一个实例执行抽样，杜绝重复建抽检单。业务逻辑本体见 {@link #sampleDailyBody}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionSamplingJob {

  /** 调度单写者锁名（P1-2）。 */
  public static final String LOCK_NAME = "schedule:inspection-sampling";

  private final ClaimCaseRepository claimCaseRepository;
  private final InspectionApplicationService inspectionApplicationService;
  private final ScheduleLockService scheduleLockService;

  /** 单批扫描上限。 */
  @Value("${bone.claim.schedule.inspection-batch:500}")
  private int batch;

  /** 抽样比例（百分比，0–100）。 */
  @Value("${bone.claim.schedule.inspection-sampling-rate:5}")
  private int samplingRatePercent;

  /** 每日 02:30（对账 01:00 之后，避开日终批处理高峰）。 */
  @Scheduled(cron = "${bone.claim.schedule.inspection-sampling-cron:0 30 2 * * ?}")
  public void sampleDaily() {
    scheduleLockService.execute(LOCK_NAME, this::sampleDailyBody);
  }

  /** 后督抽样业务逻辑本体（无锁；单测直接调用，锁由 {@link #sampleDaily} 的调度单写者包裹）。 */
  void sampleDailyBody() {
    List<ClaimTenantRef> candidates =
        claimCaseRepository.findFinishedForInspectionAllTenants(batch);
    int sampled = 0;
    int notSampled = 0;
    int alreadyInspected = 0;
    int notEligible = 0;
    int failed = 0;
    for (ClaimTenantRef ref : candidates) {
      try {
        SampleOutcome outcome =
            TenantContextRunner.callAs(
                ref.tenantId(),
                () ->
                    inspectionApplicationService.sampleIfEligible(
                        ref.claimId(), samplingRatePercent));
        switch (outcome) {
          case SAMPLED -> sampled++;
          case NOT_SAMPLED -> notSampled++;
          case ALREADY_INSPECTED -> alreadyInspected++;
          case NOT_ELIGIBLE -> notEligible++;
        }
      } catch (Exception e) {
        failed++;
        log.error("后督抽样失败: claimId={}, tenantId={}", ref.claimId(), ref.tenantId(), e);
      }
    }
    log.info(
        "[全租户扫描] 后督抽样完成: 批上限={}, 候选={}, 比例={}%, 命中建单={}, 未命中={}, 已抽检={}, 不适用={}, 失败={}（E-2 平台运维入口）",
        batch,
        candidates.size(),
        samplingRatePercent,
        sampled,
        notSampled,
        alreadyInspected,
        notEligible,
        failed);
  }
}
