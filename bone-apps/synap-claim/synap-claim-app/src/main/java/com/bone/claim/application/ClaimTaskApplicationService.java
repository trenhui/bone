package com.bone.claim.application;

import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.common.ClaimErrorCodes;
import com.bone.claim.common.ClaimErrors;
import com.bone.claim.common.PageBounds;
import com.bone.claim.domain.model.claim.ClaimCase;
import com.bone.claim.domain.model.config.FeatureToggle;
import com.bone.claim.domain.model.config.ReviewFatigueConfig;
import com.bone.claim.domain.model.task.AssignReason;
import com.bone.claim.domain.model.task.AssignmentDecision;
import com.bone.claim.domain.model.task.ClaimAssignRule;
import com.bone.claim.domain.model.task.ClaimTask;
import com.bone.claim.domain.model.task.ClaimTaskStatus;
import com.bone.claim.domain.model.task.TaskAssignmentContext;
import com.bone.claim.domain.model.task.TaskType;
import com.bone.claim.domain.port.out.AssigneeEligibilityPort;
import com.bone.claim.domain.repository.ClaimAssignRuleRepository;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import com.bone.claim.domain.repository.ClaimTaskRepository;
import com.bone.claim.domain.service.FatigueAwareEligibilityPort;
import com.bone.claim.domain.service.TaskAssigner;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 案件任务应用服务（批 1：任务分配工作台的数据入口）。
 *
 * <p><b>三条不可退让的语义</b>：
 *
 * <ol>
 *   <li><b>归属是写入期事实</b>（D4）：求解一次、写入任务行，查询侧只读。绝不"查询时现算"—— 那会让同一任务在不同时刻归属不同，批 2 的归因与批 4
 *       的超时回池都会读到漂移结果。
 *   <li><b>不臆造归属</b>：无规则 / 候选全不可用 / 开关关闭，一律创建入池任务（{@code PENDING_CLAIM} + 无 assignee），
 *       并把理由码写进行。任务必须存在，否则案件会从待办里消失。
 *   <li><b>并发自领恰 1 成功</b>：乐观锁 + 生成列唯一键两道防线，失败统一映射 409 {@code SC_CLAIM_TASK_CONFLICT}。
 *       <b>不做重试</b>——领取的语义是"我要做这件事"，重试等于替用户抢单；冲突必须让人知道。
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaimTaskApplicationService {

  /** 默认优先级（数值越小越先处理；规则命中时被规则优先级覆盖）。 */
  public static final int DEFAULT_PRIORITY = 100;

  private final ClaimTaskRepository claimTaskRepository;
  private final ClaimAssignRuleRepository claimAssignRuleRepository;
  private final ClaimCaseRepository claimCaseRepository;
  private final AssigneeEligibilityPort assigneeEligibilityPort;
  private final FeatureTenantToggleService featureTenantToggleService;
  private final TenantPort tenantPort;

  /**
   * 「我的待办」行（对外字段最小化）。
   *
   * <p><b>为什么单独定义而不是直接返回聚合</b>：聚合含 tenantId / version / assign_rule_id / deleted 等内部字段，
   * 直接序列化会把租户与并发控制细节泄露到前端；且任务行将来会加字段（due_at、transfer 痕迹）， 用聚合当契约会让前端被动跟着后端结构漂移。<b>雪花 ID 一律
   * String</b>——JS number 只有 53 位精度，63 位 ID 必失真。
   */
  public record MyTaskRow(
      String taskId,
      String taskNo,
      String claimId,
      String taskType,
      String status,
      int priority,
      String claimType,
      String sourceChannel,
      BigDecimal claimAmount,
      Instant assignedAt,
      Instant claimedAt,
      Instant createdAt) {}

  /**
   * 为案件开启一个任务（幂等）。
   *
   * <p><b>幂等由谁保证</b>：先按「同案同类型生效任务」查一次；并发下两次都没查到时会撞 {@code uk_tenant_active} 唯一键，{@code
   * DuplicateKeyException} 被翻译成「已存在」而不是错误—— 案件状态跃迁的事件可能被重放（at-least-once），重放不得产生第二条任务。
   */
  @Transactional
  public MyTaskRow openForClaim(long claimId, TaskType taskType) {
    if (taskType == null) {
      throw new DomainException("任务类型不能为空");
    }
    ClaimTask existing = claimTaskRepository.findActiveByClaimAndType(claimId, taskType.name());
    if (existing != null) {
      return toRow(existing);
    }
    ClaimCase claim = claimCaseRepository.findById(claimId);
    if (claim == null) {
      throw ClaimErrors.of(ClaimErrorCodes.CLAIM_NOT_FOUND, claimId);
    }
    long tenantId = tenantPort.currentTenantId();
    TaskAssignmentContext ctx =
        new TaskAssignmentContext(
            taskType, claim.getClaimType(), claim.getSourceChannel(), claim.getClaimAmount());
    AssignmentDecision decision = decide(tenantId, ctx);
    long id = DistributedIdGenerator.generateLongId();
    Instant now = Instant.now();
    ClaimTask task = ClaimTask.open(id, tenantId, "T" + id, claimId, ctx, DEFAULT_PRIORITY, now);
    task.applyAssignment(decision, now);
    try {
      claimTaskRepository.insert(task);
    } catch (DuplicateKeyException e) {
      // 并发建任务：另一个事务先插入了同案同类型的生效任务 ⇒ 幂等返回既有行，不制造第二条。
      log.info("[TASK_OPEN] 并发建任务撞唯一键，返回既有任务: claimId={} type={}", claimId, taskType);
      ClaimTask concurrent = claimTaskRepository.findActiveByClaimAndType(claimId, taskType.name());
      if (concurrent == null) {
        throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT, claimId, e);
      }
      return toRow(concurrent);
    }
    log.info(
        "[TASK_OPEN] taskId={} claimId={} type={} status={} reason={} ruleId={}",
        task.getId(),
        claimId,
        taskType,
        task.getStatus(),
        task.getAssignReason(),
        task.getAssignRuleId());
    return toRow(task);
  }

  /**
   * 自领任务（并发恰 1 成功）。
   *
   * <p><b>为什么冲突不重试</b>：乐观锁失败意味着「我读到的版本已被别人改掉」。重试会重新加载一条 已被他人领取的任务，然后在 {@code claim}
   * 里因状态检查再失败——绕一圈仍要报冲突，只是把错误 推迟到更远的调用栈，并且中间多写了一次事务。
   */
  @Transactional
  public MyTaskRow claim(long taskId, String operatorId) {
    ClaimTask task = claimTaskRepository.findById(taskId);
    if (task == null) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_NOT_FOUND, taskId);
    }
    Instant now = Instant.now();
    try {
      task.claim(operatorId, now);
    } catch (DomainException e) {
      // 领域层只表达"不许"，协议码由应用层给（同 ClaimCaseApplicationService 的闸门纪律）。
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT, e.getMessage());
    }
    try {
      claimTaskRepository.update(task);
    } catch (OptimisticLockingFailureException | DuplicateKeyException e) {
      throw ClaimErrors.of(ClaimErrorCodes.SC_CLAIM_TASK_CONFLICT, taskId, e);
    }
    log.info("[TASK_CLAIM] taskId={} operator={} status={}", taskId, operatorId, task.getStatus());
    return toRow(task);
  }

  /**
   * 我的待办分页。
   *
   * <p><b>total 与列表同源</b>：仓储侧 {@code findMyTasks} 与 {@code countMyTasks} 共用同一份过滤条件 （私有 {@code
   * myTaskCriteria}），应用层只负责组装。造 137 条待办时 total 必须是 137、逐页之和与 ID 去重也都是 137（判据见验证报告）。
   */
  @Transactional(readOnly = true)
  public PageResult<MyTaskRow> myTasks(String assigneeId, String status, int page, int size) {
    long tenantId = tenantPort.currentTenantId();
    PageBounds bounds = PageBounds.of(page, size);
    List<ClaimTask> rows =
        claimTaskRepository.findMyTasks(tenantId, assigneeId, status, bounds.page(), bounds.size());
    long total = claimTaskRepository.countMyTasks(tenantId, assigneeId, status);
    return PageResult.of(
        rows.stream().map(ClaimTaskApplicationService::toRow).toList(),
        total,
        bounds.page(),
        bounds.size());
  }

  /** 某案任务列表（案件 360 视图；含终态）。 */
  @Transactional(readOnly = true)
  public List<MyTaskRow> tasksOfClaim(long claimId) {
    return claimTaskRepository.findByClaimId(claimId).stream()
        .map(ClaimTaskApplicationService::toRow)
        .toList();
  }

  /**
   * 求解归属（开关 + 规则 + 准入）。
   *
   * <p><b>开关关闭时任务仍创建</b>：只跳过求解，理由码记 {@code NO_RULE}（灰度统计的时间窗口内 命中率为 0
   * 是事实而非缺陷）。若关闭开关连任务都不建，关闭动作就会把案件从待办里抹掉。
   */
  private AssignmentDecision decide(long tenantId, TaskAssignmentContext ctx) {
    boolean autoAssign =
        featureTenantToggleService.isFeatureAllowed(
            tenantId, true, FeatureToggle.FEATURE_AUTO_ASSIGN);
    if (!autoAssign) {
      log.info("[TASK_ASSIGN] skipped=TOGGLE_OFF tenantId={} type={}", tenantId, ctx.taskType());
      return AssignmentDecision.pool(AssignReason.NO_RULE);
    }
    List<ClaimAssignRule> rules =
        claimAssignRuleRepository.findCandidateRules(ctx.taskType().name());
    ReviewFatigueConfig config = featureTenantToggleService.reviewFatigueConfig(tenantId);
    if (config == null) {
      config = ReviewFatigueConfig.defaults();
    }
    Instant dayStart = utcDayStart(Instant.now());
    AssigneeEligibilityPort guarded =
        new FatigueAwareEligibilityPort(
            assigneeEligibilityPort,
            config,
            assigneeId -> {
              try {
                return claimTaskRepository.countFinishedTodayByAssignee(
                    tenantId, assigneeId, dayStart);
              } catch (RuntimeException e) {
                // 计数不可用 ⇒ 候选不可用。绝不外溢异常把收单链拖挂，也不把未知状态当可用。
                log.warn(
                    "[FATIGUE_GUARD] 完成量取数失败，候选按不可用处理：tenantId={}, assigneeId={}, err={}",
                    tenantId,
                    assigneeId,
                    e.getMessage());
                return -1L;
              }
            });
    return TaskAssigner.decide(ctx, rules, guarded);
  }

  /** UTC 当日零点（与仓储/评估端点同一口径，避免按服务器时区漂移出「两班次」）。 */
  static Instant utcDayStart(Instant now) {
    return now.atZone(java.time.ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(java.time.ZoneOffset.UTC)
        .toInstant();
  }

  private static MyTaskRow toRow(ClaimTask t) {
    return new MyTaskRow(
        String.valueOf(t.getId()),
        t.getTaskNo(),
        String.valueOf(t.getClaimId()),
        t.getTaskType(),
        t.getStatus(),
        t.getPriority() == null ? DEFAULT_PRIORITY : t.getPriority(),
        t.getClaimType(),
        t.getSourceChannel(),
        t.getClaimAmount(),
        t.getAssignedAt(),
        t.getClaimedAt(),
        t.getCreatedAt());
  }

  /** 供事件监听器判定「是否已存在生效任务」用（避免监听器直接依赖仓储）。 */
  @Transactional(readOnly = true)
  public boolean hasActiveTask(long claimId, TaskType taskType) {
    ClaimTask existing = claimTaskRepository.findActiveByClaimAndType(claimId, taskType.name());
    return existing != null && ClaimTaskStatus.activeNames().contains(existing.getStatus());
  }
}
