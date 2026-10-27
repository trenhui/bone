package com.bone.workflow.runtime;

import com.bone.workflow.domain.ConditionContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowBranchEvaluator;
import com.bone.workflow.domain.WorkflowConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.ApproverResolver;
import com.bone.workflow.ports.spi.NotificationPort;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 审批流引擎默认实现：流程推进 + 任务完成判定 + 审批人指派。
 *
 * <p><b>四条硬纪律</b>：
 *
 * <ol>
 *   <li><b>不直写业务单据状态</b>：只经 {@link WorkflowStatusPort} 表达意图，由宿主走平台状态机落地，避免与 {@code status} 形成双真源。
 *   <li><b>解析不出审批人就挂起，不放行</b>：组织架构断链（员工没有主管）是常态，把它当「跳过该节点」等于让单据 无人审批直接通过——这是审批系统最严重的事故形态。故置 {@code
 *       SUSPENDED} 并留痕待人工介入。
 *   <li><b>任务决策走 CAS</b>：读-判-写在两个审批人同时点「同意」时会双写、重复推进流程。
 *   <li><b>节点推进走存储层原子判定</b>：「是否已齐」与「推进」必须同一条原子操作。会签若在应用层读两次快照， 两个审批人可能互读「对方还没通过」→
 *       <b>流程永远卡在待办</b>（最经典的会签死锁）。
 * </ol>
 *
 * <p>本类不依赖 Spring（由 starter 装配），也不依赖平台任何类，故可被任意宿主复用。
 */
public class WorkflowEngineImpl implements WorkflowEngine {

  private static final Logger LOG = LoggerFactory.getLogger(WorkflowEngineImpl.class);

  /** 单次推进的最大步数（防定义异常导致死循环；发布期校验已禁环，此为双保险）。 */
  private static final int MAX_ADVANCE_STEPS = 64;

  /**
   * 同实例「驳回退回」次数上限（循环防护）。
   *
   * <p>驳回退回是运行期跳转，不限制就可能 A 驳回 B、B 驳回 A 无限往返——单据永远卡在「被驳回→退回→再被驳」的拉锯里，
   * 既无终态、也无业务进展。达上限即强制整单驳回终止，把「拉锯」变成确定性的「流程结束」。 计数口径：每发生一次驳回（整单驳回或退回节点都会留下一条 {@code REJECTED}
   * 任务），计数 +1。
   */
  private static final int MAX_REJECT_TO_NODE_ROUNDS = 10;

  private final WorkflowDefinitionRepository definitionRepository;
  private final WorkflowInstanceRepository instanceRepository;
  private final ApproverResolver approverResolver;
  private final WorkflowRecordPort recordPort;
  private final WorkflowStatusPort statusPort;
  private final WorkflowAuditPort auditPort;
  private final WorkflowIdGenerator idGenerator;
  private final WorkflowConditionEvaluator conditionEvaluator;
  private final WorkflowBranchEvaluator branchEvaluator;
  private final WorkflowDefinitionValidator validator;
  private final Clock clock;
  private final NotificationPort notificationPort;

  public WorkflowEngineImpl(
      WorkflowDefinitionRepository definitionRepository,
      WorkflowInstanceRepository instanceRepository,
      ApproverResolver approverResolver,
      WorkflowRecordPort recordPort,
      WorkflowStatusPort statusPort,
      WorkflowAuditPort auditPort,
      WorkflowIdGenerator idGenerator,
      WorkflowConditionEvaluator conditionEvaluator,
      WorkflowDefinitionValidator validator,
      Clock clock) {
    this(
        definitionRepository,
        instanceRepository,
        approverResolver,
        recordPort,
        statusPort,
        auditPort,
        idGenerator,
        conditionEvaluator,
        validator,
        clock,
        NotificationPort.NOOP);
  }

  public WorkflowEngineImpl(
      WorkflowDefinitionRepository definitionRepository,
      WorkflowInstanceRepository instanceRepository,
      ApproverResolver approverResolver,
      WorkflowRecordPort recordPort,
      WorkflowStatusPort statusPort,
      WorkflowAuditPort auditPort,
      WorkflowIdGenerator idGenerator,
      WorkflowConditionEvaluator conditionEvaluator,
      WorkflowDefinitionValidator validator,
      Clock clock,
      NotificationPort notificationPort) {
    this.definitionRepository = definitionRepository;
    this.instanceRepository = instanceRepository;
    this.approverResolver = approverResolver;
    this.recordPort = recordPort;
    this.statusPort = statusPort;
    this.auditPort = auditPort;
    this.idGenerator = idGenerator;
    this.conditionEvaluator = conditionEvaluator;
    this.branchEvaluator = new WorkflowBranchEvaluator(conditionEvaluator);
    this.validator = validator;
    this.clock = clock == null ? Clock.systemUTC() : clock;
    this.notificationPort = notificationPort == null ? NotificationPort.NOOP : notificationPort;
  }

  /**
   * 发送通知的兜底包装：<b>通知失败绝不阻断审批主链路</b>。
   *
   * <p>审批通过 / 驳回是主链路，必须成功；站内信 / 推送是尽力而为。若因为「短信网关挂了」就让审批失败， 是把次要故障升级成主要故障。此处再包一层
   * try-catch（宿主实现理应自捕，双保险）。
   */
  private void notify(Runnable notification) {
    try {
      notification.run();
    } catch (RuntimeException ex) {
      LOG.warn("[workflow] 通知发送失败（不影响审批主链路）", ex);
    }
  }

  @Override
  public WorkflowTask reassignTask(
      String taskId, String newAssigneeId, String operatorId, String reason) {
    if (newAssigneeId == null || newAssigneeId.isBlank()) {
      throw new WorkflowEngineException(WorkflowErrorCode.NO_ASSIGNEE, "改派目标办理人不能为空");
    }
    WorkflowTask task =
        instanceRepository
            .findTask(taskId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.TASK_NOT_FOUND, "任务不存在：" + taskId));
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_DECIDED, "任务已决策，不可改派：" + task.status());
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可改派：" + instance.phase());
    }
    if (task.assigneeId().equals(newAssigneeId)) {
      return task; // 幂等：改派给自己，无需落库
    }
    // 内控红线：改派目标不得是发起人本人——「自己提单、自己审批」等于这道审批事实上不存在。
    requireNotSelfApproval(instance, newAssigneeId);
    // 条件更新：仅当任务仍为 PENDING 时才改办理人。失败说明办理人几乎同时完成了决策——
    // 这种交错下「改派一个已办结的任务」毫无意义，必须拒绝而不是覆盖。
    if (!instanceRepository.reassignTask(taskId, newAssigneeId, operatorId)) {
      throw new WorkflowEngineException(WorkflowErrorCode.CONFLICT, "任务已被他人处理，改派失败：" + taskId);
    }
    WorkflowTask reassigned =
        new WorkflowTask(
            task.id(),
            task.instanceId(),
            task.nodeCode(),
            newAssigneeId,
            task.status(),
            task.decision(),
            task.comment(),
            task.dueAt(),
            task.createdAt(),
            task.decidedAt());
    auditPort.recordReassignment(task, newAssigneeId, operatorId, reason);
    notify(
        () ->
            notificationPort.notifyReassigned(
                reassigned, instance, newAssigneeId, operatorId, reason));
    LOG.info(
        "[workflow] 任务改派：task={} {} → {} operator={} reason={}",
        taskId,
        task.assigneeId(),
        newAssigneeId,
        operatorId,
        reason);
    return reassigned;
  }

  @Override
  public WorkflowTask urgeTask(String taskId, String operatorId, String comment) {
    WorkflowTask task =
        instanceRepository
            .findTask(taskId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.TASK_NOT_FOUND, "任务不存在：" + taskId));
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_DECIDED, "任务已决策，无需催办：" + task.status());
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，无需催办：" + instance.phase());
    }
    // 催办只发通知 + 留痕，不改变任务与流程状态——催办是「提醒」，不是「决策」。
    auditPort.recordUrge(task, operatorId, comment);
    notify(() -> notificationPort.notifyUrged(task, instance, operatorId, comment));
    LOG.info(
        "[workflow] 任务催办：task={} assignee={} operator={} comment={}",
        taskId,
        task.assigneeId(),
        operatorId,
        comment);
    return task;
  }

  @Override
  public WorkflowTask transferTask(
      String taskId, String newAssigneeId, String operatorId, String comment) {
    if (newAssigneeId == null || newAssigneeId.isBlank()) {
      throw new WorkflowEngineException(WorkflowErrorCode.NO_ASSIGNEE, "转办目标办理人不能为空");
    }
    WorkflowTask task =
        instanceRepository
            .findTask(taskId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.TASK_NOT_FOUND, "任务不存在：" + taskId));
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_DECIDED, "任务已决策或已转交，不可再转办：" + task.status());
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可转办：" + instance.phase());
    }
    // 转办是审批人本人把待办交出去——操作人必须是当前办理人。管理员干预走 reassignTask。
    if (!task.assigneeId().equals(operatorId)) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.NOT_ASSIGNEE, "只有任务当前办理人本人可以转办：" + taskId);
    }
    if (task.assigneeId().equals(newAssigneeId)) {
      return task; // 幂等：转办给自己，无需落库
    }
    // 内控红线：不得转办给发起人本人（否则发起人拿到自己单据的审批权）。
    requireNotSelfApproval(instance, newAssigneeId);
    // CAS 标记原任务为 TRANSFERRED：仅当任务仍为 PENDING 时才成功，避免与「他人同时决策 / 转办」互相覆盖。
    // TRANSFERRED 不在节点完成的决策集（APPROVED/REJECTED/CANCELLED）内，故不会静默满足单签/或签的「任一通过」判据——
    // 办理权已整体移交新任务，原任务仅作审计留痕。
    if (!instanceRepository.compareAndSetTaskStatus(
        taskId,
        WorkflowTask.Status.PENDING,
        WorkflowTask.Status.TRANSFERRED,
        null,
        comment,
        operatorId)) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "任务已被他人处理（并发冲突），转办失败：" + taskId);
    }
    Instant now = clock.instant();
    WorkflowTask newTask =
        new WorkflowTask(
            idGenerator.nextId(),
            instance.id(),
            task.nodeCode(),
            newAssigneeId,
            WorkflowTask.Status.PENDING,
            null,
            null,
            null,
            now,
            null);
    instanceRepository.saveTask(newTask, operatorId);
    auditPort.recordTransfer(task, newAssigneeId, operatorId, comment);
    notify(() -> notificationPort.notifyTaskCreated(newTask, instance));
    LOG.info(
        "[workflow] 待办转办：task={} {} → {} operator={} comment={}",
        taskId,
        task.assigneeId(),
        newAssigneeId,
        operatorId,
        comment);
    return newTask;
  }

  @Override
  public WorkflowTask addSign(
      String taskId, String newAssigneeId, String operatorId, String comment) {
    if (newAssigneeId == null || newAssigneeId.isBlank()) {
      throw new WorkflowEngineException(WorkflowErrorCode.NO_ASSIGNEE, "加签目标办理人不能为空");
    }
    WorkflowTask task =
        instanceRepository
            .findTask(taskId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.TASK_NOT_FOUND, "任务不存在：" + taskId));
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_DECIDED, "任务已决策，不可再加签：" + task.status());
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可加签：" + instance.phase());
    }
    // 加签是审批人本人邀请协同——操作人必须是当前办理人。管理员注入走 assignTask。
    if (!task.assigneeId().equals(operatorId)) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.NOT_ASSIGNEE, "只有任务当前办理人本人可以加签：" + taskId);
    }
    // 内控红线：不得把发起人本人加成审批人（自审批）。
    requireNotSelfApproval(instance, newAssigneeId);
    // 同节点下已是待办的不重复加签（避免一个人被加两次、会签永远等不齐）。
    List<WorkflowTask> nodeTasks =
        instanceRepository.listTasksByNode(instance.id(), task.nodeCode());
    boolean alreadyPending =
        nodeTasks.stream()
            .anyMatch(
                each ->
                    each.status() == WorkflowTask.Status.PENDING
                        && each.assigneeId().equals(newAssigneeId));
    if (alreadyPending) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "该办理人已是本节点待办，不可重复加签：" + newAssigneeId);
    }
    Instant now = clock.instant();
    WorkflowTask newTask =
        new WorkflowTask(
            idGenerator.nextId(),
            instance.id(),
            task.nodeCode(),
            newAssigneeId,
            WorkflowTask.Status.PENDING,
            null,
            null,
            null,
            now,
            null);
    // 并发双点下用 saveTaskIfAbsent 去重：同人同节点只留一条待办。
    if (!instanceRepository.saveTaskIfAbsent(newTask, operatorId)) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "该办理人已是本节点待办，加签冲突：" + newAssigneeId);
    }
    auditPort.recordAddSign(task, newAssigneeId, operatorId, comment);
    notify(() -> notificationPort.notifyTaskCreated(newTask, instance));
    LOG.info(
        "[workflow] 加签：task={} node={} 发起人={} 被加签人={} operator={}",
        taskId,
        task.nodeCode(),
        task.assigneeId(),
        newAssigneeId,
        operatorId);
    return newTask;
  }

  @Override
  public WorkflowInstance start(
      String entityCode, String recordId, String definitionKey, String operatorId) {
    // 幂等：同一单据已有「未终结」的实例时直接复用，不新建第二个实例。
    // 网络重试 / 用户连点两次「提交」都会走到这里——若不幂等，会得到两个并行实例（双份审批、互相看不见）。
    Optional<WorkflowInstance> existing =
        instanceRepository.findInstanceByRecord(entityCode, recordId);
    if (existing.isPresent() && !existing.get().isTerminated()) {
      LOG.info(
          "[workflow] 复用进行中的流程实例：entity={} record={} instance={} phase={}",
          entityCode,
          recordId,
          existing.get().id(),
          existing.get().phase());
      return existing.get();
    }

    WorkflowDefinition definition =
        definitionRepository
            .findLatestPublished(definitionKey)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.DEFINITION_NOT_FOUND, "未找到已发布的流程定义：" + definitionKey));
    // 纵深防御：定义在发布期已校验过，但引擎不假设「调用方一定走发布流程」——校验成本极低（审批是低频路径），
    // 而漏校验的代价是单据无人审批。故这里再校验一次。
    List<WorkflowDefinitionValidator.Violation> violations = validator.validate(definition);
    if (!violations.isEmpty()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.DEFINITION_INVALID,
          "流程定义未通过发布校验："
              + violations.stream().map(WorkflowDefinitionValidator.Violation::message).toList());
    }
    String startCode =
        definition.nodes().stream()
            .filter(node -> node.type() == WorkflowDefinition.NodeType.START)
            .map(WorkflowDefinition.Node::code)
            .findFirst()
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.DEFINITION_MALFORMED, "流程定义缺发起节点：" + definitionKey));
    Instant now = clock.instant();
    WorkflowInstance instance =
        new WorkflowInstance(
            idGenerator.nextId(),
            entityCode,
            recordId,
            definition.key(),
            definition.version(),
            startCode,
            WorkflowInstance.Phase.RUNNING,
            operatorId,
            now,
            now);
    // 并发下的原子闸门：唯一键插入抢占「一单据一活动实例」槽位，并与实例落库<b>同事务</b>完成（claimAndSaveInstance）——否则「指针已插、实例未落」的中间态会让并发启动出现指针存在但实例行缺失的竞态。
    // 应用层「先查再建」在两个提交请求同时到达时会双建（两个并行实例、双份审批、互相看不见）。
    if (!instanceRepository.claimAndSaveInstance(instance)) {
      Optional<WorkflowInstance> raced =
          instanceRepository.findInstanceByRecord(entityCode, recordId);
      if (raced.isPresent() && !raced.get().isTerminated()) {
        LOG.info(
            "[workflow] 并发启动被去重，复用进行中的流程实例：entity={} record={} instance={}",
            entityCode,
            recordId,
            raced.get().id());
        return raced.get();
      }
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_STARTED, "该单据已存在进行中的审批流程：" + entityCode + "/" + recordId);
    }
    auditPort.recordStart(instance, operatorId);
    LOG.info(
        "[workflow] 流程启动：instance={} entity={} record={} def={} v{}",
        instance.id(),
        entityCode,
        recordId,
        definition.key(),
        definition.version());
    return advanceFrom(instance, definition, operatorId, startCode);
  }

  @Override
  public WorkflowInstance completeTask(
      String taskId, WorkflowTask.Decision decision, String comment, String operatorId) {
    return completeTask(taskId, decision, comment, operatorId, null);
  }

  @Override
  public WorkflowInstance completeTask(
      String taskId,
      WorkflowTask.Decision decision,
      String comment,
      String operatorId,
      String backToNodeCode) {
    WorkflowTask task =
        instanceRepository
            .findTask(taskId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.TASK_NOT_FOUND, "审批任务不存在：" + taskId));
    if (!task.assigneeId().equals(operatorId)) {
      throw new WorkflowEngineException(WorkflowErrorCode.NOT_ASSIGNEE, "无权处理他人审批任务：" + taskId);
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    // 幂等优先于终态判定：流程已完成后用户（或网络重试）再次提交同一结论，属正常重试，必须幂等返回而非报错。
    // 只有「尚未决策」的任务才受终态约束（那时再决策已无意义）。
    if (task.status() == WorkflowTask.Status.APPROVED
        || task.status() == WorkflowTask.Status.REJECTED) {
      if (task.decision() == decision) {
        return instance;
      }
      throw new WorkflowEngineException(
          WorkflowErrorCode.ALREADY_DECIDED, "审批任务已决策，不可改判：" + taskId);
    }
    // 终态不可回写：流程一旦通过/驳回/撤回，晚到的决策必须被拒绝，
    // 否则或签节点上「先同意后驳回」会把已 APPROVED 的流程改写成 REJECTED。
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED,
          "流程已终结（" + instance.phase() + "），不允许再决策：" + taskId);
    }
    if (task.status() != WorkflowTask.Status.PENDING) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "审批任务当前状态不允许决策：" + task.status());
    }
    // 驳回退回指定节点：跳转前先做目标合法性与循环防护校验（fail-closed），
    // 否则「先标记驳回、再发现目标非法」会留下「任务已 REJECTED、实例仍 RUNNING、却无新待办」的死节点。
    if (decision == WorkflowTask.Decision.REJECT && backToNodeCode != null) {
      validateRejectToNodeTarget(instance, task, backToNodeCode);
      long rejectRounds =
          instanceRepository.listTasks(instance.id()).stream()
              .filter(each -> each.status() == WorkflowTask.Status.REJECTED)
              .count();
      if (rejectRounds >= MAX_REJECT_TO_NODE_ROUNDS) {
        return terminateAsRejected(
            instance,
            operatorId,
            "驳回退回次数达上限（" + MAX_REJECT_TO_NODE_ROUNDS + "），防循环，流程终止：" + comment);
      }
    }
    WorkflowTask.Status nextStatus =
        decision == WorkflowTask.Decision.APPROVE
            ? WorkflowTask.Status.APPROVED
            : WorkflowTask.Status.REJECTED;
    // 驳回必须写明理由：驳回的唯一价值是「告诉发起人改哪里」。不填意见的驳回对发起人是一句「不同意」，
    // 他既不知为何被驳、也不知如何修改，只能反复重提——企业报销/采购里这是最常见的流程噪音来源。
    if (decision == WorkflowTask.Decision.REJECT && (comment == null || comment.isBlank())) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.REJECT_COMMENT_REQUIRED, "驳回必须填写审批意见：" + taskId);
    }
    boolean updated =
        instanceRepository.compareAndSetTaskStatus(
            taskId, WorkflowTask.Status.PENDING, nextStatus, decision, comment, operatorId);
    if (!updated) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "审批任务已被他人处理（并发冲突），请刷新后重试：" + taskId);
    }
    auditPort.recordDecision(task, decision, comment);
    LOG.info(
        "[workflow] 审批决策：task={} node={} assignee={} decision={}",
        taskId,
        task.nodeCode(),
        operatorId,
        decision);

    WorkflowInstance current =
        instanceRepository
            .findInstance(task.instanceId())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + task.instanceId()));
    if (current.isTerminated()) {
      return current; // 并发下他人已终止流程：幂等收敛，不再推进
    }
    WorkflowDefinition definition = definitionOf(current);
    // 驳回退回指定节点：审批人驳回时可选「退回上一节点 / 退回发起人」而非整单驳回，
    // 是企业报销/采购审批最高频诉求（钉钉/飞书/宜搭标配）。运行期跳转，不改变定义模型（审批节点仍恰 1 条出边）。
    if (decision == WorkflowTask.Decision.REJECT && backToNodeCode != null) {
      return rejectToNode(current, definition, task, operatorId, backToNodeCode, comment);
    }
    List<WorkflowDefinition.Edge> outs = definition.outEdges(task.nodeCode());
    if (outs.size() != 1) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.DEFINITION_MALFORMED,
          "审批节点 " + task.nodeCode() + " 应恰有 1 条出边，实有 " + outs.size());
    }
    WorkflowDefinition.Node node =
        definition
            .node(task.nodeCode())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.DEFINITION_MALFORMED, "流程定义缺少节点：" + task.nodeCode()));
    // 原子推进：判据（会签是否已齐 / 是否有人驳回 / 比例会签法定人数是否达标）与推进在同一条原子操作里完成。
    boolean advanced =
        instanceRepository.compareAndAdvanceNode(
            current.id(),
            task.nodeCode(),
            outs.get(0).to(),
            node.mode(),
            node.quorum(),
            operatorId);
    if (advanced) {
      // 节点已完成：作废该节点余下的待办（单签/或签解析出多人时，其余人的待办不得留成幽灵待办）。
      cancelNodeTasksWithNotice(current, task.nodeCode(), operatorId, "审批节点已决策完成，该待办无需再处理");
      return advanceFrom(current, definition, operatorId, outs.get(0).to());
    }
    // 未推进：可能是「会签未齐，等其他人」「比例会签法定人数未达」或「节点内有人驳回」。重新读该节点任务做区分。
    List<WorkflowTask> freshTasks =
        instanceRepository.listTasksByNode(current.id(), task.nodeCode());
    WorkflowInstance latest = instanceRepository.findInstance(current.id()).orElse(current);
    if (!latest.isTerminated()) {
      if (node.mode() == WorkflowDefinition.ApprovalMode.RATIO) {
        // 比例会签：仅当「即便剩余待办全部通过也凑不齐法定人数」才整单驳回；单条驳回不立即终止（容忍少数人反对）。
        long approved =
            freshTasks.stream()
                .filter(each -> each.status() == WorkflowTask.Status.APPROVED)
                .count();
        long pending =
            freshTasks.stream()
                .filter(each -> each.status() == WorkflowTask.Status.PENDING)
                .count();
        if (node.quorum() != null && approved + pending < node.quorum()) {
          return terminateAsRejected(
              latest,
              operatorId,
              "比例会签法定人数不可达（已通过 "
                  + approved
                  + " + 待决 "
                  + pending
                  + " < 法定 "
                  + node.quorum()
                  + "），流程终止："
                  + comment);
        }
      } else {
        boolean anyRejected =
            freshTasks.stream().anyMatch(each -> each.status() == WorkflowTask.Status.REJECTED);
        if (anyRejected) {
          return terminateAsRejected(latest, operatorId, comment);
        }
      }
    }
    return latest; // 会签未齐 / 比例会签法定人数未达：留在当前节点等待其余办理人
  }

  @Override
  public WorkflowInstance withdraw(String instanceId, String operatorId) {
    WorkflowInstance instance =
        instanceRepository
            .findInstance(instanceId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + instanceId));
    if (!instance.startedBy().equals(operatorId)) {
      throw new WorkflowEngineException(WorkflowErrorCode.NOT_INITIATOR, "只有发起人可以撤回流程");
    }
    // 挂起（SUSPENDED）不算终态：挂起单据必须仍可撤回，否则发起人被锁死、单据变死单。
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可撤回：" + instance.phase());
    }
    if (!instanceRepository.compareAndSetPhase(
        instanceId, instance.phase(), WorkflowInstance.Phase.WITHDRAWN, operatorId)) {
      WorkflowInstance latest = instanceRepository.findInstance(instanceId).orElse(instance);
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "流程状态已变更，撤回失败：" + latest.phase());
    }
    cancelPendingTasksWithNotice(instance, operatorId, "发起人已撤回该单据");
    WorkflowInstance withdrawn =
        new WorkflowInstance(
            instance.id(),
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            instance.currentNodeCode(),
            WorkflowInstance.Phase.WITHDRAWN,
            instance.startedBy(),
            instance.startedAt(),
            clock.instant());
    instanceRepository.saveInstance(withdrawn, operatorId);
    auditPort.recordTermination(withdrawn, operatorId, "发起人撤回");
    statusPort.markWithdrawn(instance.entityCode(), instance.recordId(), instance.id(), operatorId);
    // 终态通知：发起人要知道「我的单子被撤回了」——只改状态不通知，发起人只能靠刷单据详情感知。
    notify(() -> notificationPort.notifyTerminated(withdrawn, operatorId, "发起人撤回"));
    LOG.info("[workflow] 流程撤回：instance={} operator={}", instanceId, operatorId);
    return withdrawn;
  }

  @Override
  public WorkflowInstance resume(String instanceId, String operatorId) {
    WorkflowInstance instance =
        instanceRepository
            .findInstance(instanceId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + instanceId));
    if (!instance.startedBy().equals(operatorId)) {
      throw new WorkflowEngineException(WorkflowErrorCode.NOT_INITIATOR, "只有发起人可以恢复挂起的流程");
    }
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可恢复：" + instance.phase());
    }
    if (instance.phase() == WorkflowInstance.Phase.RUNNING) {
      return instance; // 已恢复或从未挂起：幂等
    }
    if (instance.phase() != WorkflowInstance.Phase.SUSPENDED) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程不处于挂起态，无法恢复：" + instance.phase());
    }
    // 重新解析审批人：组织架构修复后应能解析出办理人。
    WorkflowDefinition definition = definitionOf(instance);
    WorkflowDefinition.Node node =
        definition
            .node(instance.currentNodeCode())
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.DEFINITION_MALFORMED,
                        "流程定义缺少节点：" + instance.currentNodeCode()));
    Map<String, Object> data =
        recordPort.loadRecordData(instance.entityCode(), instance.recordId());
    // 只有审批节点才需要「重新解析审批人」：条件节点挂起是因<b>分支不可裁决</b>（如两条分支同时命中），
    // 恢复应直接重跑该节点；若也去解析审批人，条件节点没有指派规则 ⇒ 永远 NO_ASSIGNEE ⇒ 死单不可恢复。
    boolean approvalNode = node.type() == WorkflowDefinition.NodeType.APPROVE;
    List<String> assignees =
        approvalNode ? resolveAssignees(node, definition, instance, data) : List.of();
    if (approvalNode && assignees.isEmpty()) {
      // 组织架构仍未修复：保持挂起，明确报错，避免「恢复成功」的假象把死单变成更隐蔽的卡单。
      throw new WorkflowEngineException(
          WorkflowErrorCode.NO_ASSIGNEE, "审批人仍解析不出，无法恢复挂起：" + instance.currentNodeCode());
    }
    if (approvalNode && isSelfApproval(instance, assignees)) {
      // 自审批未解除（仍解析出发起人）：保持挂起并明确报错，不得派单给发起人。
      throw new WorkflowEngineException(
          WorkflowErrorCode.SELF_APPROVAL, "审批人仍为发起人本人（自审批），无法恢复挂起：" + instance.currentNodeCode());
    }
    // 阶段 CAS：仅当仍停在 SUSPENDED 时才恢复，避免与并发决策互相覆盖。
    boolean resumed =
        instanceRepository.compareAndSetPhase(
            instanceId,
            WorkflowInstance.Phase.SUSPENDED,
            WorkflowInstance.Phase.RUNNING,
            operatorId);
    if (!resumed) {
      WorkflowInstance latest = instanceRepository.findInstance(instanceId).orElse(instance);
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "流程状态已变更，恢复失败：" + latest.phase());
    }
    Instant now = clock.instant();
    for (String assignee : assignees) {
      WorkflowTask task =
          new WorkflowTask(
              idGenerator.nextId(),
              instance.id(),
              node.code(),
              assignee,
              WorkflowTask.Status.PENDING,
              null,
              null,
              null,
              now,
              null);
      instanceRepository.saveTask(task, operatorId);
      // 派单即通知：不通知，审批流就退化成「办事人每天自己刷待办列表」，催办与超时全都失效。
      notify(() -> notificationPort.notifyTaskCreated(task, instance));
    }
    WorkflowInstance running =
        new WorkflowInstance(
            instance.id(),
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            instance.currentNodeCode(),
            WorkflowInstance.Phase.RUNNING,
            instance.startedBy(),
            instance.startedAt(),
            now);
    instanceRepository.saveInstance(running, operatorId);
    auditPort.recordResumption(running, operatorId);
    statusPort.markResumed(instance.entityCode(), instance.recordId(), instance.id());
    LOG.info(
        "[workflow] 流程恢复：instance={} node={} assignees={}", instanceId, node.code(), assignees);
    if (!approvalNode) {
      // 非审批节点（条件分支歧义 / 抄送等）挂起：修复配置后从该节点重跑，闭合「挂起 → 恢复」通道。
      return advanceFrom(running, definition, operatorId, instance.currentNodeCode());
    }
    return running;
  }

  @Override
  public Optional<WorkflowInstance> findInstance(String entityCode, String recordId) {
    return instanceRepository.findInstanceByRecord(entityCode, recordId);
  }

  @Override
  public Optional<WorkflowInstance> findInstanceById(String instanceId) {
    return instanceRepository.findInstance(instanceId);
  }

  @Override
  public List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status) {
    if (assigneeId == null || assigneeId.isBlank()) {
      throw new WorkflowEngineException(WorkflowErrorCode.NO_ASSIGNEE, "查询个人任务列表缺少办理人");
    }
    return instanceRepository.listTasksByAssignee(assigneeId, status);
  }

  @Override
  public List<WorkflowTask> listPendingTasks(String instanceId) {
    return instanceRepository.listPendingTasks(instanceId);
  }

  @Override
  public List<WorkflowTask> listInstanceHistory(String instanceId) {
    if (instanceRepository.findInstance(instanceId).isEmpty()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + instanceId);
    }
    return instanceRepository.listTasks(instanceId);
  }

  @Override
  public WorkflowInstance assignTask(
      String instanceId, String nodeCode, String assigneeId, String operatorId, String reason) {
    if (assigneeId == null || assigneeId.isBlank()) {
      throw new WorkflowEngineException(WorkflowErrorCode.NO_ASSIGNEE, "强制指派目标办理人不能为空");
    }
    WorkflowInstance instance =
        instanceRepository
            .findInstance(instanceId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + instanceId));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可指派：" + instance.phase());
    }
    // 内控红线：管理员兜底指派同样不得指派给发起人本人。
    // 这是「自审批挂起」的唯一恢复通道——若允许指派给发起人，挂起守卫即被绕过。
    requireNotSelfApproval(instance, assigneeId);
    String targetNode =
        (nodeCode == null || nodeCode.isBlank()) ? instance.currentNodeCode() : nodeCode;
    // 条件插入去重：判据由存储层在同一条语句里算（与 compareAndAdvanceNode 同纪律）——
    // 应用层「先查无待办再插」在并发双点下会双插，同人同节点两条 PENDING 会让会签节点永远等不齐。
    WorkflowTask task =
        new WorkflowTask(
            idGenerator.nextId(),
            instance.id(),
            targetNode,
            assigneeId,
            WorkflowTask.Status.PENDING,
            null,
            null,
            null,
            clock.instant(),
            null);
    WorkflowTask created = null;
    if (instanceRepository.saveTaskIfAbsent(task, operatorId)) {
      created = task;
      // 派单即通知：被强制指派的办理人也应收到待办提醒，否则仍靠刷列表才知情。
      notify(() -> notificationPort.notifyTaskCreated(task, instance));
    }
    // SUSPENDED 无任务场景：注入任务后恢复为 RUNNING，闭合 §4.3「强制指派兜底」缺口。
    // 阶段 CAS 保证并发安全（与并发决策 / 撤回互斥）。
    WorkflowInstance result = instance;
    if (instance.phase() == WorkflowInstance.Phase.SUSPENDED) {
      if (!instanceRepository.compareAndSetPhase(
          instanceId,
          WorkflowInstance.Phase.SUSPENDED,
          WorkflowInstance.Phase.RUNNING,
          operatorId)) {
        WorkflowInstance latest = instanceRepository.findInstance(instanceId).orElse(instance);
        throw new WorkflowEngineException(
            WorkflowErrorCode.CONFLICT, "流程状态已变更，指派失败：" + latest.phase());
      }
      WorkflowInstance running =
          new WorkflowInstance(
              instance.id(),
              instance.entityCode(),
              instance.recordId(),
              instance.definitionKey(),
              instance.definitionVersion(),
              instance.currentNodeCode(),
              WorkflowInstance.Phase.RUNNING,
              instance.startedBy(),
              instance.startedAt(),
              clock.instant());
      instanceRepository.saveInstance(running, operatorId);
      auditPort.recordResumption(running, operatorId);
      statusPort.markResumed(instance.entityCode(), instance.recordId(), instance.id());
      result = running;
    }
    if (created != null) {
      // 谁把待办强制指派给了谁、为什么，必须留痕（改派是审批审计重点对象）。
      auditPort.recordReassignment(created, assigneeId, operatorId, "管理员强制指派：" + reason);
    }
    LOG.info(
        "[workflow] 强制指派：instance={} node={} assignee={} operator={} reason={}",
        instanceId,
        targetNode,
        assigneeId,
        operatorId,
        reason);
    return result;
  }

  @Override
  public WorkflowInstance terminateInstance(String instanceId, String operatorId, String reason) {
    WorkflowInstance instance =
        instanceRepository
            .findInstance(instanceId)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.INSTANCE_NOT_FOUND, "流程实例不存在：" + instanceId));
    if (instance.isTerminated()) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.INSTANCE_TERMINATED, "流程已终结，不可终止：" + instance.phase());
    }
    // 阶段 CAS：仅当仍停在当前阶段时才改，避免与并发决策 / 撤回互相覆盖。
    if (!instanceRepository.compareAndSetPhase(
        instanceId, instance.phase(), WorkflowInstance.Phase.TERMINATED, operatorId)) {
      WorkflowInstance latest = instanceRepository.findInstance(instanceId).orElse(instance);
      throw new WorkflowEngineException(
          WorkflowErrorCode.CONFLICT, "流程状态已变更，终止失败：" + latest.phase());
    }
    cancelPendingTasksWithNotice(instance, operatorId, "管理员已强制终止该流程");
    instanceRepository.cancelPendingTasks(instanceId, operatorId);
    WorkflowInstance terminated =
        new WorkflowInstance(
            instance.id(),
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            instance.currentNodeCode(),
            WorkflowInstance.Phase.TERMINATED,
            instance.startedBy(),
            instance.startedAt(),
            clock.instant());
    instanceRepository.saveInstance(terminated, operatorId);
    auditPort.recordTermination(terminated, operatorId, reason == null ? "管理员强制终止" : reason);
    statusPort.markTerminated(
        instance.entityCode(), instance.recordId(), instance.id(), operatorId, reason);
    notify(() -> notificationPort.notifyTerminated(terminated, operatorId, reason));
    LOG.info("[workflow] 流程强制终止：instance={} operator={} reason={}", instanceId, operatorId, reason);
    return terminated;
  }

  @Override
  public Map<String, Object> loadRecordData(String entityCode, String recordId) {
    return recordPort.loadRecordData(entityCode, recordId);
  }

  // ============================== 内部推进 ==============================

  /**
   * 从指定节点向前推进。
   *
   * <p>推进的每一步都通过 {@code compareAndAdvanceNode} 原子改写实例的 {@code currentNodeCode}，避免两个并发决策 各推各的。
   */
  private WorkflowInstance advanceFrom(
      WorkflowInstance instance,
      WorkflowDefinition definition,
      String operatorId,
      String currentCode) {
    Map<String, Object> data =
        recordPort.loadRecordData(instance.entityCode(), instance.recordId());
    ConditionContext ctx = ConditionContext.of(operatorId);
    String current = currentCode;
    for (int step = 0; step < MAX_ADVANCE_STEPS; step++) {
      // current 在循环内会被重赋值，lambda 只能捕获 effectively-final 的局部变量，故先取别名。
      String currentNodeCode = current;
      WorkflowDefinition.Node node =
          definition
              .node(currentNodeCode)
              .orElseThrow(
                  () ->
                      new WorkflowEngineException(
                          WorkflowErrorCode.DEFINITION_MALFORMED, "流程定义缺少节点：" + currentNodeCode));
      switch (node.type()) {
        case START -> {
          WorkflowDefinition.Edge edge = singleOutEdge(definition, currentNodeCode);
          if (!moveTo(instance, currentNodeCode, edge.to(), null, edge.label(), operatorId)) {
            return latestInstance(instance);
          }
          current = edge.to();
        }
        case CC -> {
          // 抄送（知会）：解析抄送人 → 逐人发知会 → 立即穿透到下一节点。
          // 抄送<b>不建任务、不阻塞</b>——它不是审批动作，收件人无需决策；若建了待办，
          // 会签/推进判据会把抄送人算进「未决策」集合，流程反而卡死。
          List<String> ccRecipients = resolveAssignees(node, definition, instance, data);
          if (ccRecipients.isEmpty()) {
            // 抄送人解析不出：不挂起（抄送不是阻塞动作），记 WARN 让配置问题可见即可。
            LOG.warn("[workflow] 抄送节点 {} 解析不出抄送人，跳过知会：instance={}", currentNodeCode, instance.id());
          } else {
            notify(() -> notificationPort.notifyCc(instance, currentNodeCode, ccRecipients));
            LOG.info(
                "[workflow] 抄送：instance={} node={} recipients={}",
                instance.id(),
                currentNodeCode,
                ccRecipients);
          }
          WorkflowDefinition.Edge edge = singleOutEdge(definition, currentNodeCode);
          if (!moveTo(instance, currentNodeCode, edge.to(), null, edge.label(), operatorId)) {
            return latestInstance(instance);
          }
          current = edge.to();
        }
        case CONDITION -> {
          List<WorkflowDefinition.Edge> outs = definition.outEdges(currentNodeCode);
          List<WorkflowBranchEvaluator.Branch> branches =
              outs.stream()
                  .map(
                      edge ->
                          new WorkflowBranchEvaluator.Branch(edge.to(), edge.when(), edge.label()))
                  .toList();
          WorkflowBranchEvaluator.Selection selection;
          try {
            selection = branchEvaluator.select(branches, data, ctx);
          } catch (WorkflowEngineException branchFailure) {
            // 分支不可裁决（多条同时命中 / 无匹配 / 条件非法）：此刻任务已决策、实例已推进到本条件节点，
            // 直接抛出会留下「停在条件节点、无待办、无告警」的死单。故先按 fail-closed 三件套挂起告警，
            // 再把稳定错误码抛给宿主翻译（422）——管理员修复条件配置后可 resume 续跑。
            suspend(
                instance,
                node,
                operatorId,
                "条件节点 " + currentNodeCode + " 分支不可裁决：" + branchFailure.getMessage());
            throw branchFailure;
          }
          if (!moveTo(
              instance,
              currentNodeCode,
              selection.to(),
              null,
              selection.matchedLabel(),
              operatorId)) {
            return latestInstance(instance);
          }
          current = selection.to();
        }
        case APPROVE -> {
          List<String> assignees = resolveAssignees(node, definition, instance, data);
          if (assignees.isEmpty()) {
            suspend(instance, node, operatorId, "审批节点 " + node.code() + " 解析不出审批人");
            return latestInstance(instance);
          }
          // 内控红线（职责分离）：解析出的审批人若含发起人本人，一律挂起而不派单。
          // 为什么不做「自动跳过发起人」：跳过 = 少一道审批，单据表面通过但内控环节缺失，比挂起更隐蔽、
          // 审计更难发现。挂起则把问题显式暴露给管理员，经 assignTask 指派他人即可续跑。
          if (isSelfApproval(instance, assignees)) {
            suspend(
                instance, node, operatorId, "审批节点 " + node.code() + " 解析出的审批人含发起人本人（自审批，违反职责分离）");
            return latestInstance(instance);
          }
          Instant now = clock.instant();
          for (String assignee : assignees) {
            WorkflowTask task =
                new WorkflowTask(
                    idGenerator.nextId(),
                    instance.id(),
                    node.code(),
                    assignee,
                    WorkflowTask.Status.PENDING,
                    null,
                    null,
                    null,
                    now,
                    null);
            instanceRepository.saveTask(task, operatorId);
            // 派单即通知：不通知，审批流就退化成「办事人每天自己刷待办列表」，催办与超时全都失效。
            notify(() -> notificationPort.notifyTaskCreated(task, instance));
          }
          LOG.info(
              "[workflow] 派单：instance={} node={} assignees={}",
              instance.id(),
              node.code(),
              assignees);
          return latestInstance(instance); // 停在审批节点，等待人工决策
        }
        case END -> {
          boolean terminated =
              instanceRepository.compareAndSetPhase(
                  instance.id(),
                  WorkflowInstance.Phase.RUNNING,
                  WorkflowInstance.Phase.APPROVED,
                  operatorId);
          if (!terminated) {
            return latestInstance(instance);
          }
          WorkflowInstance approved =
              new WorkflowInstance(
                  instance.id(),
                  instance.entityCode(),
                  instance.recordId(),
                  instance.definitionKey(),
                  instance.definitionVersion(),
                  node.code(),
                  WorkflowInstance.Phase.APPROVED,
                  instance.startedBy(),
                  instance.startedAt(),
                  clock.instant());
          instanceRepository.saveInstance(approved, operatorId);
          auditPort.recordTermination(approved, operatorId, "流程通过");
          statusPort.markApproved(
              instance.entityCode(), instance.recordId(), instance.id(), operatorId);
          notify(() -> notificationPort.notifyTerminated(approved, operatorId, "流程通过"));
          LOG.info("[workflow] 流程通过：instance={}", instance.id());
          return approved;
        }
        default -> throw new WorkflowEngineException(
            WorkflowErrorCode.DEFINITION_MALFORMED, "未知节点类型：" + node.type());
      }
    }
    throw new WorkflowEngineException(
        WorkflowErrorCode.DEFINITION_MALFORMED, "流程推进步数超限（疑似死循环），节点：" + current);
  }

  /**
   * 原子地把实例从一个节点移到下一个节点。
   *
   * @param completion 完成条件（{@code null} = 无条件移动，用于 START / CC / CONDITION 节点）
   * @return 是否移动成功（false = 他人已推进，调用方应重新读实例收敛）
   */
  private boolean moveTo(
      WorkflowInstance instance,
      String fromNode,
      String toNode,
      WorkflowDefinition.ApprovalMode completion,
      String branchLabel,
      String operatorId) {
    boolean moved =
        instanceRepository.compareAndAdvanceNode(
            instance.id(), fromNode, toNode, completion, null, operatorId);
    if (moved) {
      auditPort.recordAdvance(instance, fromNode, toNode, branchLabel);
      LOG.info("[workflow] 节点推进：instance={} {} → {}", instance.id(), fromNode, toNode);
    }
    return moved;
  }

  /**
   * 挂起：解析不出审批人。绝不静默放行。
   *
   * <p><b>为什么必须检查阶段 CAS 的返回值</b>：撤销 / 驳回可能并发把实例改写成终态。若此处忽略返回值，随后 {@code saveInstance(suspended)}
   * 会把整行（含 phase）覆写成 SUSPENDED——一个已 WITHDRAWN 且活动指针已删的实例「复活」，同一单据可再次
   * start，出现<b>双活动实例</b>（活动指针表要防的正是这个）。resume / withdraw 都检查了返回值，唯此路径遗漏。
   */
  /**
   * 挂起（fail-closed 三件套：挂起 + 审计 + 告警）。
   *
   * <p><b>为什么挂起而不是抛异常了事</b>：本方法被调用时，前序状态变更（任务决策 / 节点推进）往往已经落库。
   * 若只抛异常，实例会停在某个<b>既无待办、又无任何告警</b>的节点上—— 没人知道它卡住，管理员只能靠翻库发现。挂起 + 管理员告警把「静默死单」变成「可见待处理」， 配合 {@code
   * resume} / {@code assignTask} / {@code terminateInstance} 三条人工介入通道形成闭环。
   */
  private void suspend(
      WorkflowInstance instance, WorkflowDefinition.Node node, String operatorId, String reason) {
    boolean suspended =
        instanceRepository.compareAndSetPhase(
            instance.id(),
            WorkflowInstance.Phase.RUNNING,
            WorkflowInstance.Phase.SUSPENDED,
            operatorId);
    if (!suspended) {
      // 他人已把实例推到终态（撤回 / 驳回 / 已挂起）：以最新状态收敛，不得覆写
      WorkflowInstance latest = latestInstance(instance);
      LOG.warn("[workflow] 挂起未生效（实例已被并发改写为 {}）：instance={}", latest.phase(), instance.id());
      return;
    }
    WorkflowInstance suspendedInstance =
        new WorkflowInstance(
            instance.id(),
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            node.code(),
            WorkflowInstance.Phase.SUSPENDED,
            instance.startedBy(),
            instance.startedAt(),
            clock.instant());
    instanceRepository.saveInstance(suspendedInstance, operatorId);
    auditPort.recordSuspension(suspendedInstance, reason);
    statusPort.markSuspended(instance.entityCode(), instance.recordId(), instance.id(), reason);
    // 挂起必须告警：不通知运维，卡单就变成无人知晓的死单。
    notify(() -> notificationPort.notifySuspended(suspendedInstance, reason));
    LOG.warn("[workflow] 流程挂起：instance={} {}", instance.id(), reason);
  }

  /** 解析节点审批人（去重且保序）。 */
  private List<String> resolveAssignees(
      WorkflowDefinition.Node node,
      WorkflowDefinition definition,
      WorkflowInstance instance,
      Map<String, Object> data) {
    List<WorkflowDefinition.AssignmentRule> rules =
        definition.assignments().getOrDefault(node.code(), List.of());
    Set<String> assignees = new LinkedHashSet<>();
    ApproverResolver.ApproverContext context =
        new ApproverResolver.ApproverContext(
            instance.entityCode(), instance.recordId(), instance.startedBy(), data);
    for (WorkflowDefinition.AssignmentRule rule : rules) {
      assignees.addAll(approverResolver.resolve(rule, node, context));
    }
    return List.copyOf(assignees);
  }

  /**
   * 自审批判定（职责分离红线）：解析出的审批人里含发起人本人。
   *
   * <p>不做「自动剔除发起人继续跑」：剔除等于<b>少一道审批</b>，单据表面通过而内控环节缺失， 比挂起更隐蔽、更难被审计发现。故一律视为不可派单。
   */
  private boolean isSelfApproval(WorkflowInstance instance, List<String> assignees) {
    return instance.startedBy() != null && assignees.contains(instance.startedBy());
  }

  /** 自审批守卫：指派 / 改派 / 转办 / 加签的目标人都不得是发起人本人。 */
  private void requireNotSelfApproval(WorkflowInstance instance, String candidate) {
    if (instance.startedBy() != null && instance.startedBy().equals(candidate)) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.SELF_APPROVAL, "审批人不得为发起人本人（自审批，违反职责分离）：" + candidate);
    }
  }

  /**
   * 作废实例余下待办，并<b>逐人知会</b>。
   *
   * <p>为什么必须先取快照再作废：{@code cancelPendingTasks} 是存储层批量更新，返回影响行数而非任务明细，
   * 事后无从得知「谁被作废」。故先读待办快照、作废后逐人发站内信——否则办理人的视角就是<b>待办凭空消失</b>：
   * 既不知要不要处理，也不知单据去向（还在等他？已被驳回？）。这是审批系统最典型的「静默丢失」缺口。
   */
  private void cancelPendingTasksWithNotice(
      WorkflowInstance instance, String operatorId, String reason) {
    List<WorkflowTask> pending = instanceRepository.listPendingTasks(instance.id());
    instanceRepository.cancelPendingTasks(instance.id(), operatorId);
    for (WorkflowTask task : pending) {
      notify(() -> notificationPort.notifyTaskCancelled(task, instance, reason));
    }
  }

  /** 作废某节点余下待办并逐人知会（节点已决策完成，同节点其余人的待办不得留成幽灵待办）。 */
  private void cancelNodeTasksWithNotice(
      WorkflowInstance instance, String nodeCode, String operatorId, String reason) {
    List<WorkflowTask> pending =
        instanceRepository.listTasksByNode(instance.id(), nodeCode).stream()
            .filter(each -> each.status() == WorkflowTask.Status.PENDING)
            .toList();
    instanceRepository.cancelPendingTasksOfNode(instance.id(), nodeCode, operatorId);
    for (WorkflowTask task : pending) {
      notify(() -> notificationPort.notifyTaskCancelled(task, instance, reason));
    }
  }

  /**
   * 校验驳回退回目标节点的合法性（fail-closed，CAS 之前调用，避免留下死节点）。
   *
   * <p>目标节点必须存在、且非结束节点、且非当前节点（自身循环无意义）；否则抛 {@code REJECT_TARGET_NODE_INVALID}， 绝不静默整单驳回冒充「退回」。
   */
  private void validateRejectToNodeTarget(
      WorkflowInstance instance, WorkflowTask task, String backToNodeCode) {
    WorkflowDefinition definition = definitionOf(instance);
    WorkflowDefinition.Node target =
        definition
            .node(backToNodeCode)
            .orElseThrow(
                () ->
                    new WorkflowEngineException(
                        WorkflowErrorCode.REJECT_TARGET_NODE_INVALID,
                        "驳回退回的目标节点不存在：" + backToNodeCode));
    // 退回结束节点 = 直接「通过」，语义错误；退回自己所在节点 = 空转无意义。两者都 fail-closed 拒绝。
    if (target.type() == WorkflowDefinition.NodeType.END) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.REJECT_TARGET_NODE_INVALID, "驳回不得退回至结束节点：" + backToNodeCode);
    }
    if (backToNodeCode.equals(task.nodeCode())) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.REJECT_TARGET_NODE_INVALID,
          "驳回退回的目标节点不能是当前节点（自身循环无意义）：" + backToNodeCode);
    }
  }

  /**
   * 驳回退回指定节点（运行期跳转；目标合法性与循环防护已在 {@link #completeTask} 的 CAS 之前校验过）。
   *
   * <p>当前节点的驳回任务已落库（{@code REJECTED}），把实例的 {@code currentNodeCode} 用与推进同源的原子 CAS 从当前节点改到目标节点， 再经
   * {@link #advanceFrom} 重新进入目标节点——审批节点重新派单、条件节点重新求值、抄送节点重新知会，复用全部 fail-closed
   * 纪律（解析不出人⇒挂起、分支歧义⇒挂起可恢复）。当前节点余下待办（单签/或签节点上其他办理人）在跳转前逐人知会作废，不留幽灵待办。
   *
   * @param instance 驳回决策已落库后的最新实例
   * @param definition 流程定义
   * @param task 被驳回的任务（其 {@code nodeCode()} 为当前节点）
   * @param operatorId 操作人（审批人本人）
   * @param backToNodeCode 退回的目标节点编码（已校验合法）
   * @param comment 驳回意见
   */
  private WorkflowInstance rejectToNode(
      WorkflowInstance instance,
      WorkflowDefinition definition,
      WorkflowTask task,
      String operatorId,
      String backToNodeCode,
      String comment) {
    // 跳转前作废当前节点余下待办并逐人知会：否则单签/或签节点上其他办理人的待办会留成幽灵待办（见 cancelNodeTasksWithNotice）。
    cancelNodeTasksWithNotice(instance, task.nodeCode(), operatorId, "审批被驳回退回，该节点余下待办作废");
    // 运行期跳转：currentNodeCode 从当前节点原子改到目标节点（无条件移动，复用 moveTo 的 CAS 语义）。
    if (!moveTo(instance, task.nodeCode(), backToNodeCode, null, "驳回退回", operatorId)) {
      return latestInstance(instance); // 并发下他人已推进：以最新状态收敛，不覆盖
    }
    LOG.info(
        "[workflow] 驳回退回：instance={} {} → {} operator={} comment={}",
        instance.id(),
        task.nodeCode(),
        backToNodeCode,
        operatorId,
        comment);
    // 重新进入目标节点：审批节点重新派单（退回重审）/ 条件节点重新求值 / 抄送重新知会/ 发起节点重启流程。
    // 该路径复用 advanceFrom 的全部 fail-closed 纪律（解析不出人⇒挂起、分支歧义⇒挂起可恢复），退回通道闭环。
    return advanceFrom(instance, definition, operatorId, backToNodeCode);
  }

  /** 驳回终止（阶段 CAS 保证不覆盖已被推进为通过的终态）。 */
  private WorkflowInstance terminateAsRejected(
      WorkflowInstance instance, String operatorId, String reason) {
    boolean terminated =
        instanceRepository.compareAndSetPhase(
            instance.id(), instance.phase(), WorkflowInstance.Phase.REJECTED, operatorId);
    if (!terminated) {
      return latestInstance(instance);
    }
    cancelPendingTasksWithNotice(instance, operatorId, reason == null ? "审批驳回" : reason);
    WorkflowInstance rejected =
        new WorkflowInstance(
            instance.id(),
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            instance.currentNodeCode(),
            WorkflowInstance.Phase.REJECTED,
            instance.startedBy(),
            instance.startedAt(),
            clock.instant());
    instanceRepository.saveInstance(rejected, operatorId);
    auditPort.recordTermination(rejected, operatorId, reason == null ? "审批驳回" : reason);
    statusPort.markRejected(
        instance.entityCode(), instance.recordId(), instance.id(), operatorId, reason);
    notify(() -> notificationPort.notifyTerminated(rejected, operatorId, reason));
    LOG.info("[workflow] 流程驳回：instance={} reason={}", instance.id(), reason);
    return rejected;
  }

  private WorkflowInstance latestInstance(WorkflowInstance fallback) {
    return instanceRepository.findInstance(fallback.id()).orElse(fallback);
  }

  private WorkflowDefinition definitionOf(WorkflowInstance instance) {
    return definitionRepository
        .findByVersion(instance.definitionKey(), instance.definitionVersion())
        .orElseThrow(
            () ->
                new WorkflowEngineException(
                    WorkflowErrorCode.DEFINITION_NOT_FOUND,
                    "流程定义版本缺失：" + instance.definitionKey() + " v" + instance.definitionVersion()));
  }

  private WorkflowDefinition.Edge singleOutEdge(WorkflowDefinition definition, String nodeCode) {
    List<WorkflowDefinition.Edge> outs = definition.outEdges(nodeCode);
    if (outs.size() != 1) {
      throw new WorkflowEngineException(
          WorkflowErrorCode.DEFINITION_MALFORMED,
          "节点 " + nodeCode + " 应恰有 1 条出边，实有 " + outs.size());
    }
    return outs.get(0);
  }

  /** 条件求值器缺省实现（装配层可替换为与平台 UI 协议规则同源的实现）。 */
  public static WorkflowConditionEvaluator defaultConditionEvaluator() {
    return new StructuredConditionEvaluator();
  }
}
