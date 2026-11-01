package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.domain.model.reconciliation.ReconciliationAction;
import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.claim.domain.model.reconciliation.projection.PendingDiff;
import com.bone.claim.domain.repository.ReconciliationActionRepository;
import com.bone.claim.domain.repository.ReconciliationDiffRepository;
import com.bone.core.annotation.NoDomainEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对账差异的<b>处置</b>（§11.7 差异闭环：检出 → 派单 → 处置 → 留痕）。
 *
 * <p><b>它补的是哪一环</b>：对账每天产出差异，但在此之前 {@code t_reconciliation_diff.resolution} 恒为 {@code PENDING}
 * 且没有任何写路径——运维把一条差异看完了也<b>没有办法把它标成已处理</b>。待处置清单只增不减， 两周后就没人再看，而这正是"有巡检、没处置"的系统最常见的死法。
 *
 * <p><b>为什么不直接改差异行、还要另记流水</b>：差异行记的是<b>当前处置态</b>（一行、便于按 {@code PENDING} 过滤排队，
 * 也让"重复处置"能被拦住），处置流水记的是<b>全过程</b>（每次动作都留操作人与说明，删不掉、改不了）。 两者不是同一事实的两份拷贝：要把行改回去必须有一条流水解释它，而流水无法被行覆盖。
 *
 * <p><b>为什么不需要双人复核</b>：处置结论（RESOLVED/ADJUSTED/WAIVED）是判定与说明，不是钱。真正动钱的动作只有两种，都不在此：
 * 调账走补偿流水（§6.4，已有双人复核），投影重算走 {@code QuotaProjectionRepairService}（无目标值入参、只写派生列）。
 * 给"标记已处理"加审批，只会让人误以为它是改账入口。
 *
 * <p><b>并发</b>：两个运维同时处置同一条差异时，各自读到 {@code PENDING} 都可能通过前置检查（先读后写）。后写者覆盖先写者的
 * 处置态，但<b>两条流水都在</b>，处置历史完整——所以丢的是"当前态"的一次覆盖，不是审计证据。要彻底消除需给差异行加乐观锁列， 那要求先改生效通道的表结构（{@code
 * t_reconciliation_diff} 现无 {@code version} 列），已记为待办。
 */
@Slf4j
@NoDomainEvent
@Service
@RequiredArgsConstructor
public class ReconciliationDispositionService {

  /** 待处置队列单次返回上限（防止运营页一次拉全表）。 */
  private static final int MAX_PENDING_LIMIT = 500;

  private final ReconciliationDiffRepository diffRepository;
  private final ReconciliationActionRepository actionRepository;
  private final TenantPort tenantPort;

  /**
   * 一次处置的结果。
   *
   * @param diffId 差异 ID
   * @param resolution 生效的处置结论
   * @param operatorId 处置人
   * @param note 处置说明（落处置流水的 {@code detail}，可为空）
   * @param occurredAt 处置时间（以处置流水为准；差异行没有 {@code resolved_at} 列，见 {@code ReconciliationDiff} 类注释）
   */
  public record Disposition(
      Long diffId, String resolution, String operatorId, String note, Instant occurredAt) {}

  /**
   * 给一条差异下处置结论（{@code PENDING} → 终态，单向一次）。
   *
   * @param diffId 差异 ID
   * @param resolution {@code RESOLVED} / {@code ADJUSTED} / {@code WAIVED}
   * @param note 处置说明（WAIVED 时必须写清理由，否则下一轮排障仍从这里开始）
   * @param operatorId 处置人（鉴权身份，不接受请求体自报）
   */
  @Transactional
  public Disposition resolveDiff(long diffId, String resolution, String note, String operatorId) {
    ReconciliationDiff diff = diffRepository.findDiff(diffId);
    if (diff == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_RECON_DIFF_NOT_FOUND, diffId);
    }
    if (!diff.isPending()) {
      // 不静默幂等：处置结论有语义（三种结论对应三种后续动作），第二次提交可能带着不同结论
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_RECON_DIFF_CONFLICT,
          "差异已处置: diffId=" + diffId + ", resolution=" + diff.getResolution());
    }
    if (resolution == null || !ReconciliationDiff.RESOLUTIONS.contains(resolution)) {
      throw ClaimErrors.of(
          ClaimErrorCodes.CLAIM_RECON_DIFF_CONFLICT,
          "非法处置结论: " + resolution + "，允许 " + ReconciliationDiff.RESOLUTIONS);
    }
    diff.resolve(resolution, operatorId);
    diffRepository.save(diff);
    Instant occurredAt = Instant.now();
    actionRepository.save(
        ReconciliationAction.diffResolved(
            tenantPort.currentTenantId(), diffId, resolution, note, operatorId, occurredAt));
    log.info(
        "[对账差异处置] operator={}, diffId={}, type={}, resolution={}, note={}",
        operatorId,
        diffId,
        diff.getDiffType(),
        resolution,
        note);
    return new Disposition(diffId, resolution, operatorId, note == null ? "" : note, occurredAt);
  }

  /**
   * 待处置队列（没有处置流水的差异，按发现时间倒序）。
   *
   * @param limit 条数上限；≤0 按 1 处理，&gt;{@value #MAX_PENDING_LIMIT} 截到上限
   */
  public List<PendingDiff> pending(int limit) {
    int effective = Math.min(Math.max(limit, 1), MAX_PENDING_LIMIT);
    return diffRepository.findPending(effective);
  }

  /** 某条差异的处置历史（按发生顺序；正常只有一条，多次出现说明被更正过）。 */
  public List<Disposition> history(long diffId) {
    List<Disposition> history = new ArrayList<>();
    for (ReconciliationAction action :
        actionRepository.findByTarget(ReconciliationAction.TARGET_DIFF, diffId)) {
      history.add(
          new Disposition(
              action.getTargetId(),
              action.getResolution(),
              action.getOperatorId(),
              action.getDetail(),
              action.getOccurredAt()));
    }
    return history;
  }
}
