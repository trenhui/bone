package com.bone.claim.adapter.web;

import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import java.util.List;

/**
 * 工作台「任务维」→「案件状态」映射（对外契约适配，属 adapter 职责）。
 *
 * <p><b>为什么要有这一层</b>：delivery/05 的 {@code GET /api/v1/review/tasks} 以<b>任务维</b> 4 值（{@code
 * WAITING/ASSIGNED/PROCESSING/DONE}）筛选待办，而本模块状态机只有<b>案件状态</b>（§6.2）。任务维若直接塞进案件状态机，等于让
 * 同一事实有两个状态源；反过来若只在契约里写而不映射，则「按 status 过滤」会被静默忽略（永远返回全部待办）。
 *
 * <p><b>映射口径</b>（刻意合并，避免自造状态）：
 *
 * <ul>
 *   <li>{@code WAITING}（待领取）→ {@code PENDING_ADJUST}(40)；
 *   <li>{@code ASSIGNED}（已分配未处理）/ {@code PROCESSING}（处理中）→ {@code ADJUSTING}(41)：案件模型里「已领取」即
 *       「处理中」，没有独立的「分配但未领取」态，两者同映一值而不另建状态；
 *   <li>{@code DONE}（已处理）→ {@code ADJUSTED}/{@code REVIEWING}/{@code FINISHED}/{@code
 *       CLOSED_REJECTED}。
 * </ul>
 *
 * <p><b>不指定 status</b> 时取工作台默认待办口径（40 + 41），而不是全量状态——把已结案案件混进待办列表是最常见的工单误派来源。
 */
public final class ReviewTaskStatusMapper {

  /** 对外任务维（delivery/05 §一 工作台任务状态）。 */
  public enum TaskStatus {
    WAITING,
    ASSIGNED,
    PROCESSING,
    DONE
  }

  private static final List<ClaimStatus> DEFAULT_TODO =
      List.of(ClaimStatus.PENDING_ADJUST, ClaimStatus.ADJUSTING);

  private ReviewTaskStatusMapper() {}

  /** 任务维 → 案件状态集（{@code null} 取默认待办口径）。 */
  public static List<ClaimStatus> toClaimStatuses(TaskStatus taskStatus) {
    if (taskStatus == null) {
      return DEFAULT_TODO;
    }
    return switch (taskStatus) {
      case WAITING -> List.of(ClaimStatus.PENDING_ADJUST);
      case ASSIGNED, PROCESSING -> List.of(ClaimStatus.ADJUSTING);
      case DONE -> List.of(
          ClaimStatus.ADJUSTED,
          ClaimStatus.REVIEWING,
          ClaimStatus.FINISHED,
          ClaimStatus.CLOSED_REJECTED);
    };
  }
}
