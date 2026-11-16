package com.bone.claim.domain.model.task;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 案件任务（{@code t_claim_task}，一案多任务）。
 *
 * <p><b>为什么归属必须是独立行而不是案件上的一列</b>：归属是<b>事实</b>（D4 写入期分配、查询期只读）。
 * 事实需要「谁、何时、按哪条规则、因为什么」四要素，案件上的单列只能留最后一次的结果； 一案多任务（初审/补件/复审/抽检）更是单列无法表达的多值关系。
 *
 * <p><b>并发自领的互斥由谁保证</b>：
 *
 * <ul>
 *   <li>数据库层：生成列 {@code active_key = claim_id:task_type}（终态为 NULL）+ {@code UNIQUE(tenant_id,
 *       active_key)} 保证「同案同类型至多一条生效任务」——两个并发建任务的事务里必有一个撞唯一键；
 *   <li>行级层：{@link Version} 乐观锁保证「同一任务被两人同时领取」时只有一个 UPDATE 生效， 另一个抛出 {@code
 *       OptimisticLockingFailureException}，应用层映射为 {@code SC_CLAIM_TASK_CONFLICT}。
 * </ul>
 *
 * <p><b>为什么不用 {@code SELECT ... FOR UPDATE}</b>：项目架构规范禁用行锁读（会把连接池与事务边界绑死， 且与乐观锁 double
 * 保护无收益），本聚合只用版本号 + 唯一键两道防线。
 *
 * <p><b>领取失败必须报冲突而不是静默入池</b>：把「已被别人领走」悄悄改成「你可以继续领」会让两个人同时处理同一案件， 产生两份互相覆盖的理算结论——那是金额错误，不是体验问题。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("t_claim_task")
public class ClaimTask extends TenantAggregateRoot<Long> {

  /** 任务号长度上限（与 DDL varchar(64) 对齐）。 */
  public static final int MAX_TASK_NO_LENGTH = 64;

  @Column(name = "task_no", nullable = false)
  private String taskNo;

  @Column(name = "claim_id", nullable = false)
  private Long claimId;

  @Column(name = "task_type", nullable = false)
  private String taskType;

  @Column(name = "status", nullable = false)
  private String status;

  @Column(name = "assignee_type")
  private String assigneeType;

  @Column(name = "assignee_id")
  private String assigneeId;

  @Column(name = "assign_dept_id")
  private String assignDeptId;

  @Column(name = "assign_rule_id")
  private Long assignRuleId;

  @Column(name = "assign_reason")
  private String assignReason;

  @Column(name = "priority", nullable = false)
  private Integer priority;

  @Column(name = "claim_type", nullable = false)
  private String claimType;

  @Column(name = "source_channel", nullable = false)
  private String sourceChannel;

  @Column(name = "claim_amount", nullable = false)
  private BigDecimal claimAmount;

  @Column(name = "assigned_at")
  private Instant assignedAt;

  @Column(name = "claimed_at")
  private Instant claimedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "due_at")
  private Instant dueAt;

  @Version
  @Column(name = "version")
  private Long version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /**
   * 创建任务（入池形态，{@code status=PENDING_CLAIM}，无归属人）。
   *
   * <p>入池是<b>默认且安全</b>的形态：即使后续求解失败或开关关闭，任务也已存在且可被领取， 不会因为「分配不到人」而让案件从待办里消失。
   */
  public static ClaimTask open(
      Long id,
      Long tenantId,
      String taskNo,
      Long claimId,
      TaskAssignmentContext snapshot,
      int priority,
      Instant now) {
    if (taskNo == null || taskNo.isBlank()) {
      throw new DomainException("任务号不能为空");
    }
    if (taskNo.length() > MAX_TASK_NO_LENGTH) {
      throw new DomainException("任务号超长（上限 " + MAX_TASK_NO_LENGTH + " 字符）");
    }
    if (claimId == null) {
      throw new DomainException("任务必须关联案件");
    }
    if (snapshot == null) {
      throw new DomainException("任务必须携带分配求解快照");
    }
    ClaimTask t = new ClaimTask();
    t.setId(id);
    t.setTenantId(tenantId);
    t.taskNo = taskNo;
    t.claimId = claimId;
    t.taskType = snapshot.taskType().name();
    t.status = ClaimTaskStatus.PENDING_CLAIM.name();
    t.priority = priority;
    t.claimType = snapshot.claimType();
    t.sourceChannel = snapshot.sourceChannel();
    t.claimAmount = snapshot.claimAmount() == null ? BigDecimal.ZERO : snapshot.claimAmount();
    t.createdAt = now;
    t.updatedAt = now;
    return t;
  }

  /**
   * 应用求解结论：命中规则则指派，否则标记理由码并入池。
   *
   * <p><b>不得在入池分支写入任何 assignee</b>：{@code assign_reason} 只描述「为什么没有人」， 它不构成归属。查询侧凡是要「归属人」的地方都读
   * {@code assignee_id}，读不到就是没有——不臆造。
   */
  public void applyAssignment(AssignmentDecision decision, Instant now) {
    if (decision == null) {
      throw new DomainException("分配结论不能为空");
    }
    requireStatus(ClaimTaskStatus.PENDING_CLAIM, "只有入池任务可以应用分配结论");
    this.updatedAt = now;
    if (!decision.assigned()) {
      this.assignReason = decision.reason().name();
      return;
    }
    ClaimAssignRule rule = decision.rule();
    this.assignReason = AssignReason.RULE_MATCH.name();
    this.assignRuleId = rule.getId();
    this.assigneeType = rule.getAssigneeType();
    this.assigneeId = rule.getAssigneeId();
    this.assignDeptId = rule.getAssignDeptId();
    this.priority = rule.getPriority() == null ? this.priority : rule.getPriority();
    this.status = ClaimTaskStatus.ASSIGNED.name();
    this.assignedAt = now;
  }

  /**
   * 自领（PENDING_CLAIM／本人已指派 → PROCESSING）。
   *
   * <p><b>先判状态再判归属</b>：已 {@code PROCESSING} 意味着已经有人在做，无论来人是谁都必须冲突 （同一任务不能有两个处理人）；{@code ASSIGNED}
   * 但归属是别人时同样冲突，否则「指派」形同虚设。
   */
  public void claim(String operatorId, Instant now) {
    if (operatorId == null || operatorId.isBlank()) {
      throw new DomainException("领取人不能为空（必须取认证主体，不接受客户端声明）");
    }
    if (ClaimTaskStatus.PROCESSING.name().equals(status)) {
      throw new DomainException(
          "任务已被" + (operatorId.equals(assigneeId) ? "本人" : "他人") + "领取，不可重复领取");
    }
    if (ClaimTaskStatus.DONE.name().equals(status)
        || ClaimTaskStatus.CANCELLED.name().equals(status)) {
      throw new DomainException("任务已进入终态，不可领取");
    }
    if (ClaimTaskStatus.ASSIGNED.name().equals(status)
        && assigneeId != null
        && !assigneeId.isBlank()
        && !assigneeId.equals(operatorId)) {
      throw new DomainException("任务已指派给他人，不可领取（需要转派请走转交流程）");
    }
    this.assigneeType = AssigneeType.USER.name();
    this.assigneeId = operatorId;
    this.status = ClaimTaskStatus.PROCESSING.name();
    this.claimedAt = now;
    this.updatedAt = now;
    if (this.assignReason == null) {
      this.assignReason = AssignReason.RULE_MATCH.name();
    }
  }

  /** 完成（终态）。 */
  public void complete(Instant now) {
    if (ClaimTaskStatus.DONE.name().equals(status)) {
      return;
    }
    if (ClaimTaskStatus.CANCELLED.name().equals(status)) {
      throw new DomainException("已取消的任务不能标记完成");
    }
    this.status = ClaimTaskStatus.DONE.name();
    this.finishedAt = now;
    this.updatedAt = now;
  }

  /** 取消（终态；案件撤件、任务合并等）。 */
  public void cancel(Instant now) {
    if (ClaimTaskStatus.CANCELLED.name().equals(status)) {
      return;
    }
    if (ClaimTaskStatus.DONE.name().equals(status)) {
      throw new DomainException("已完成的任务不能取消");
    }
    this.status = ClaimTaskStatus.CANCELLED.name();
    this.finishedAt = now;
    this.updatedAt = now;
  }

  /** 当前状态是否为生效态（与生成列口径同源）。 */
  public boolean isActive() {
    for (ClaimTaskStatus s : ClaimTaskStatus.values()) {
      if (s.name().equals(status)) {
        return s.isActive();
      }
    }
    return false;
  }

  private void requireStatus(ClaimTaskStatus expected, String message) {
    if (!expected.name().equals(status)) {
      throw new DomainException(message + "（当前 " + status + "）");
    }
  }
}
