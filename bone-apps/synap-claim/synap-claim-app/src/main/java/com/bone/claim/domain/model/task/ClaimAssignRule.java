package com.bone.claim.domain.model.task;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 案件分配规则（{@code t_claim_assign_rule}）。
 *
 * <p><b>为什么是「规则表 + 固定求解顺序」而不是规则引擎</b>：匹配维度只有 4 个（任务类型 / 险种 / 金额段 / 渠道），
 * 且要求可解释、可审计。引入表达式引擎会带来新依赖与不可回退的表达式面，而运营真正需要的是 「为什么这条任务分给了这个人」——一个 {@code assign_rule_id}
 * 就能回答。故优先级列 + 主键定序即是全部灵活性。
 *
 * <p><b>求解顺序必须确定</b>：{@code priority} 升序，同值按 {@code id} 升序。若允许「同优先级随机取一条」， 同一任务在不同时刻归属不同，批 2 的 AI
 * 归因与批 4 的超时回池都会读到漂移的归属事实。
 *
 * <p><b>金额段是左闭右开</b>：{@code amount_min ≤ amount < amount_max}。写成双闭区间会让边界金额同时命中 两条相邻规则（如 [0,1000] 与
 * [1000,5000] 都命中 1000），而「按优先级取第一条」会把边界值静默归到先配的那条规则上。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_assign_rule")
public class ClaimAssignRule extends TenantAggregateRoot<Long> {

  /** 任务类型通配符：一条规则适用全部类型。 */
  public static final String ANY_TASK_TYPE = "*";

  @Column(name = "rule_name", nullable = false)
  private String ruleName;

  @Column(name = "task_type", nullable = false)
  private String taskType;

  @Column(name = "claim_type", nullable = false)
  private String claimType;

  @Column(name = "source_channel", nullable = false)
  private String sourceChannel;

  @Column(name = "amount_min")
  private BigDecimal amountMin;

  @Column(name = "amount_max")
  private BigDecimal amountMax;

  @Column(name = "assignee_type", nullable = false)
  private String assigneeType;

  @Column(name = "assignee_id", nullable = false)
  private String assigneeId;

  @Column(name = "assign_dept_id")
  private String assignDeptId;

  @Column(name = "priority", nullable = false)
  private Integer priority;

  @Column(name = "enabled", nullable = false)
  private Boolean enabled;

  @Column(name = "remark")
  private String remark;

  @Version
  @Column(name = "version")
  private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** 是否启用（{@code enabled=1}）。 */
  public boolean isEnabled() {
    return Boolean.TRUE.equals(enabled);
  }

  /**
   * 创建规则（配置面入口 + 测试夹具）。
   *
   * <p><b>为什么任务类型/目标类型用字符串而不是枚举</b>：这两列是从配置面（导入 / 运营后台）进来的外部输入，
   * 用字符串接收再在读取期归一，才能在写入期拒绝非法值的同时容忍历史脏数据（读取期判为不可用， 而不是让一行脏配置把整个求解打挂）。
   *
   * @param taskType 任务类型或 {@code *}
   * @param assigneeType {@code USER}/{@code DEPT}/{@code ROLE}（非法值在求解期判不可用）
   */
  public static ClaimAssignRule create(
      Long id,
      Long tenantId,
      String ruleName,
      String taskType,
      String claimType,
      String sourceChannel,
      BigDecimal amountMin,
      BigDecimal amountMax,
      String assigneeType,
      String assigneeId,
      String assignDeptId,
      int priority,
      boolean enabled,
      Instant now) {
    if (ruleName == null || ruleName.isBlank()) {
      throw new IllegalArgumentException("规则名不能为空");
    }
    if (taskType == null || taskType.isBlank()) {
      throw new IllegalArgumentException("任务类型不能为空（适用全部类型请用 *）");
    }
    if (amountMin != null && amountMax != null && amountMin.compareTo(amountMax) >= 0) {
      throw new IllegalArgumentException("金额段下限必须小于上限（左闭右开）");
    }
    ClaimAssignRule r = new ClaimAssignRule();
    r.setId(id);
    r.setTenantId(tenantId);
    r.ruleName = ruleName;
    r.taskType = taskType.trim();
    r.claimType = claimType == null ? "" : claimType.trim();
    r.sourceChannel = sourceChannel == null ? "" : sourceChannel.trim();
    r.amountMin = amountMin;
    r.amountMax = amountMax;
    r.assigneeType = assigneeType;
    r.assigneeId = assigneeId == null ? "" : assigneeId.trim();
    r.assignDeptId = assignDeptId;
    r.priority = priority;
    r.enabled = enabled;
    r.createdAt = now;
    r.updatedAt = now;
    return r;
  }

  /**
   * 规则目标是否配置完整。
   *
   * <p><b>空目标不是「分给空」而是「不可用」</b>：导入的半截规则（{@code assignee_id} 为空串）若被当成有效候选， 领取侧就会拿到一个空 assignee
   * 的"已指派"任务——它既不在任何人的待办里，也不在部门池里， 等于任务凭空消失。故空目标在求解期即判为不可用，让任务入池。
   */
  public boolean hasTarget() {
    return assigneeId != null && !assigneeId.isBlank();
  }

  /** 目标主体类型（非法值返回 null，由求解期判为不可用——不静默当成 USER）。 */
  public AssigneeType targetType() {
    if (assigneeType == null) {
      return null;
    }
    for (AssigneeType t : AssigneeType.values()) {
      if (t.name().equals(assigneeType)) {
        return t;
      }
    }
    return null;
  }

  /**
   * 是否匹配本次求解上下文。
   *
   * <p>空维度＝不限制（通配）；任务类型支持显式 {@code *} 通配。金额段左闭右开，缺边界即单侧开放。 <b>金额未知（null）不命中任何设了金额段的规则</b>：把 null 当
   * 0 会让「大额专属组」静默吞下所有金额缺失的案件。
   */
  public boolean matches(TaskAssignmentContext ctx) {
    if (ctx == null) {
      return false;
    }
    if (!ANY_TASK_TYPE.equals(taskType) && !ctx.taskType().name().equals(taskType)) {
      return false;
    }
    if (claimType != null && !claimType.isBlank() && !claimType.equals(ctx.claimType())) {
      return false;
    }
    if (sourceChannel != null
        && !sourceChannel.isBlank()
        && !sourceChannel.equals(ctx.sourceChannel())) {
      return false;
    }
    return amountInRange(ctx.claimAmount());
  }

  private boolean amountInRange(BigDecimal amount) {
    if (amountMin == null && amountMax == null) {
      return true;
    }
    if (amount == null) {
      return false;
    }
    if (amountMin != null && amount.compareTo(amountMin) < 0) {
      return false;
    }
    return amountMax == null || amount.compareTo(amountMax) < 0;
  }
}
