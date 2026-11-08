package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.claim.ClaimCaseTrack;
import com.bone.claim.domain.model.claim.valueobject.ClaimStage;
import com.bone.claim.domain.model.sla.ClaimCaseSla;
import com.bone.claim.domain.model.sla.WorkHours;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimCaseSlaRepository;
import com.bone.claim.domain.repository.ClaimCaseTrackRepository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SLA 物化应用服务（ADR-003 / §6.6 运营读模型）。
 *
 * <p>从 {@code t_claim_case_track}（事件溯源真源）派生阶段停留与挂起占用，物化写入 {@code t_claim_case_sla}。由 {@code
 * adapter.schedule} 的 {@code RefreshLimitHourJob} 每 20 分钟逐租户驱动（ADR-0030 平台运维旁路）。
 *
 * <p><b>实现口径（§6.6 {@code limit_hour}）</b>：{@code remaining_work_hours = max(0, 阶段目标工时 − 阶段已耗工作日小时 −
 * 挂起占用工作日小时)}；工作日 = 周一至周五（{@link WorkHours}，周末重叠剔除）。{@code legal_due_at} 取受理 + 30 自然日（法定核定，
 * 不可放宽）；{@code sla_due_at} 取阶段进入 + 目标工时自然小时（一期近似，精确反向工作日历为二期）。
 *
 * <p><b>为何不回写 {@code t_claim_case.limit_hour}</b>：每 20 分钟写 {@code t_claim_case.updated_at}
 * 会让超期关单扫描（{@code CloseExpiredClaimJob} 依赖 {@code updated_at < before}）永远命中不到滞留案件。故 {@code
 * limit_hour} 口径由本读模型 {@code remaining_work_hours} 承载，{@code t_claim_case.limit_hour} 列保留但不作为刷新目标。
 *
 * <p><b>E-5.4 豁免</b>：本类只物化读模型、不产生业务领域事件（{@link NoDomainEvent}）；领域事件已由发起状态跃迁的聚合发布。
 */
@Slf4j
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class SlaApplicationService {

  private static final long LEGAL_DUE_DAYS = 30L;

  private final ClaimCaseRepository claimCaseRepository;
  private final ClaimCaseTrackRepository trackRepository;
  private final ClaimCaseSlaRepository slaRepository;
  private final TenantPort tenantPort;

  /**
   * 物化单个案件的 SLA 快照（须在租户上下文内调用：{@code TenantContextRunner.runAs}）。
   *
   * <p>幂等：每次以 track 全量重算并 upsert；并发刷新冲突（{@link OptimisticLockingFailureException}）时跳过本轮，下轮自然收敛。
   */
  @Transactional
  public void refreshForClaim(long claimId) {
    refreshForClaimAt(claimId, Instant.now());
  }

  /** 可注入时钟的物化入口（单测确定性用）。 */
  void refreshForClaimAt(long claimId, Instant now) {
    long tenantId = tenantPort.currentTenantId();
    ClaimCase claim = claimCaseRepository.findById(claimId);
    if (claim == null || !Long.valueOf(tenantId).equals(claim.getTenantId())) {
      return;
    }
    if (claim.finished()) {
      return; // 终态不参与作业 SLA
    }

    List<ClaimCaseTrack> tracks = trackRepository.findByClaimId(tenantId, claimId);
    Instant stageEnteredAt = tracks.isEmpty() ? claim.getCreatedAt() : lastEventTime(tracks);
    HangAccum hang = accumulateHang(tracks, now);

    BigDecimal base = ClaimStage.ofName(claim.getStage()).baseWorkHours();
    BigDecimal elapsed = WorkHours.elapsedWorkHours(stageEnteredAt, now);
    BigDecimal remaining = base.subtract(elapsed).subtract(hang.workHours());
    if (remaining.signum() < 0) {
      remaining = BigDecimal.ZERO.setScale(2);
    }

    Instant legalDueAt = claim.getCreatedAt().plus(Duration.ofDays(LEGAL_DUE_DAYS));
    Instant slaDueAt = WorkHours.plusNaturalHours(stageEnteredAt, base);

    try {
      ClaimCaseSla existing = slaRepository.findByClaimId(tenantId, claimId);
      if (existing == null) {
        slaRepository.save(
            ClaimCaseSla.create(
                tenantId,
                claimId,
                claim.getStatus(),
                claim.getStage(),
                stageEnteredAt,
                legalDueAt,
                slaDueAt,
                remaining,
                hang.seconds(),
                now));
      } else {
        existing.refresh(
            claim.getStatus(),
            claim.getStage(),
            stageEnteredAt,
            legalDueAt,
            slaDueAt,
            remaining,
            hang.seconds(),
            now);
        slaRepository.save(existing);
      }
    } catch (OptimisticLockingFailureException e) {
      log.debug("SLA 物化并发冲突，跳过本轮: claimId={}, tenantId={}", claimId, tenantId);
    }
  }

  private static Instant lastEventTime(List<ClaimCaseTrack> tracks) {
    ClaimCaseTrack last = tracks.get(tracks.size() - 1);
    return last.getCreatedAt().toInstant();
  }

  /** 从 track（HANG_UP/RESUME）累计挂起自然秒数与其工作日小时数（未恢复的挂起计至 now）。 */
  private static HangAccum accumulateHang(List<ClaimCaseTrack> tracks, Instant now) {
    Instant hangStart = null;
    long seconds = 0L;
    BigDecimal workHours = BigDecimal.ZERO;
    for (ClaimCaseTrack track : tracks) {
      Instant at = track.getCreatedAt().toInstant();
      if ("HANG_UP".equals(track.getActionCode())) {
        if (hangStart == null) {
          hangStart = at;
        }
      } else if ("RESUME".equals(track.getActionCode()) && hangStart != null) {
        seconds += Duration.between(hangStart, at).getSeconds();
        workHours = workHours.add(WorkHours.elapsedWorkHours(hangStart, at));
        hangStart = null;
      }
    }
    if (hangStart != null) {
      seconds += Duration.between(hangStart, now).getSeconds();
      workHours = workHours.add(WorkHours.elapsedWorkHours(hangStart, now));
    }
    return new HangAccum(seconds, workHours);
  }

  private record HangAccum(long seconds, BigDecimal workHours) {}
}
