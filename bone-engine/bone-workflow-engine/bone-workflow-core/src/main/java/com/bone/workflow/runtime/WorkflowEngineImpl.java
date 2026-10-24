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
    WorkflowTask.Status nextStatus =
        decision == WorkflowTask.Decision.APPROVE
            ? WorkflowTask.Status.APPROVED
            : WorkflowTask.Status.REJECTED;
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
    // 原子推进：判据（会签是否已齐 / 是否有人驳回）与推进在同一条原子操作里完成。
    boolean advanced =
        instanceRepository.compareAndAdvanceNode(
            current.id(), task.nodeCode(), outs.get(0).to(), node.mode(), operatorId);
    if (advanced) {
      // 节点已完成：作废该节点余下的待办（单签/或签解析出多人时，其余人的待办不得留成幽灵待办）。
      instanceRepository.cancelPendingTasksOfNode(current.id(), task.nodeCode(), operatorId);
      return advanceFrom(current, definition, operatorId, outs.get(0).to());
    }
    // 未推进：可能是「会签未齐，等其他人」或「节点内有人驳回」。重新读该节点任务做区分。
    List<WorkflowTask> freshTasks =
        instanceRepository.listTasksByNode(current.id(), task.nodeCode());
    boolean anyRejected =
        freshTasks.stream().anyMatch(each -> each.status() == WorkflowTask.Status.REJECTED);
    WorkflowInstance latest = instanceRepository.findInstance(current.id()).orElse(current);
    if (anyRejected && !latest.isTerminated()) {
      return terminateAsRejected(latest, operatorId, comment);
    }
    return latest; // 会签未齐：留在当前节点等待其余办理人
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
    instanceRepository.cancelPendingTasks(instanceId, operatorId);
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
    List<String> assignees = resolveAssignees(node, definition, instance, data);
    if (assignees.isEmpty()) {
      // 组织架构仍未修复：保持挂起，明确报错，避免「恢复成功」的假象把死单变成更隐蔽的卡单。
      throw new WorkflowEngineException(
          WorkflowErrorCode.NO_ASSIGNEE, "审批人仍解析不出，无法恢复挂起：" + instance.currentNodeCode());
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
    return running;
  }

  @Override
  public Optional<WorkflowInstance> findInstance(String entityCode, String recordId) {
    return instanceRepository.findInstanceByRecord(entityCode, recordId);
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
        case START, CC -> {
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
          WorkflowBranchEvaluator.Selection selection = branchEvaluator.select(branches, data, ctx);
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
            suspend(instance, node, operatorId);
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
            instance.id(), fromNode, toNode, completion, operatorId);
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
  private void suspend(WorkflowInstance instance, WorkflowDefinition.Node node, String operatorId) {
    String reason = "审批节点 " + node.code() + " 解析不出审批人";
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

  /** 驳回终止（阶段 CAS 保证不覆盖已被推进为通过的终态）。 */
  private WorkflowInstance terminateAsRejected(
      WorkflowInstance instance, String operatorId, String reason) {
    boolean terminated =
        instanceRepository.compareAndSetPhase(
            instance.id(), instance.phase(), WorkflowInstance.Phase.REJECTED, operatorId);
    if (!terminated) {
      return latestInstance(instance);
    }
    instanceRepository.cancelPendingTasks(instance.id(), operatorId);
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
