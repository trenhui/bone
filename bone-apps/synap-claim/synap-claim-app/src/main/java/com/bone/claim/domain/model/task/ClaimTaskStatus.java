package com.bone.claim.domain.model.task;

import java.util.Set;

/**
 * 任务状态机：{@code PENDING_CLAIM}（入池待领）→ {@code ASSIGNED}（已指派）→ {@code PROCESSING}（已领取在办） → {@code
 * DONE}／{@code CANCELLED}（终态）。
 *
 * <p><b>「生效」与「终态」必须显式区分</b>：数据库唯一键 {@code uk_tenant_active} 靠生成列 {@code active_key} 在终态返回 NULL
 * 来放行多条历史行。若这里把终态写成「非生效即终态」的隐式判断， 那么新增一个中间态（如 {@code TRANSFERRED}）会被数据库当成终态而不占唯一性，
 * 「同案同类型至多一条生效任务」当场失效。 所以本枚举把两个集合都列出来，新增状态必须同时决定它属于哪一边。
 */
public enum ClaimTaskStatus {
  /** 入池待领：无归属人，部门池内任一人可领。 */
  PENDING_CLAIM(true),
  /** 已指派：有明确归属人，但尚未开始处理。 */
  ASSIGNED(true),
  /** 已领取在办。 */
  PROCESSING(true),
  /** 已完成（正常终态）。 */
  DONE(false),
  /** 已取消（终态：案件撤件、任务合并等）。 */
  CANCELLED(false);

  private final boolean active;

  ClaimTaskStatus(boolean active) {
    this.active = active;
  }

  /** 是否占用「同案同类型唯一」名额（必须与 0125 生成列的 {@code status IN (...)} 保持一致）。 */
  public boolean isActive() {
    return active;
  }

  /** 是否终态（不可再跃迁）。 */
  public boolean isTerminal() {
    return !active;
  }

  /** 生效状态名集合：生成列与读侧 SQL 的唯一口径来源。 */
  public static Set<String> activeNames() {
    return Set.of(PENDING_CLAIM.name(), ASSIGNED.name(), PROCESSING.name());
  }
}
