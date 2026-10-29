package com.bone.workflow.starter.adapter;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.starter.sdk.WfActiveInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfInstanceRow;
import com.bone.workflow.starter.sdk.WfInstanceSdkRepository;
import com.bone.workflow.starter.sdk.WfTaskRow;
import com.bone.workflow.starter.sdk.WfTaskSdkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程实例 / 任务仓储的平台实现（bone-metadata-sdk {@code @Sql} → {@code wf_instance} / {@code wf_task}）。
 *
 * <p><b>持久化通道</b>：全部经 SDK {@code @Sql} 代理（HC-001/006）——2026-10-24 自裸 JDBC 收敛， {@code
 * sdk-persistence-bypass-baseline.json} 的对应豁免已移除。SQL 与并发口径（三 CAS、条件插入、 实例终态守卫）沉淀在 {@link
 * WfInstanceSdkRepository} / {@link WfTaskSdkRepository} 的方法文档， H2 原样执行做回归。
 *
 * <p><b>租户纪律</b>：引擎 API 不带 tenantId，故每条调用都显式补 {@code tenant_id}，由 {@link TenantContext}
 * 提供；上下文缺失即失败关闭（不静默查全租户）。
 *
 * <p><b>审计归属纪律</b>：写审计列的 {@code operatorId} 一律取端口方法入参（引擎用例签名均持有操作人）， <b>不得</b>从 {@link
 * TenantContext} 取租户 id 冒充——审计列要回答「谁」，租户 id 回答「哪个租户」。
 */
public class SdkWorkflowInstanceRepository implements WorkflowInstanceRepository {

  private final WfInstanceSdkRepository instances;
  private final WfTaskSdkRepository tasks;
  private final WfActiveInstanceSdkRepository activePointers;

  public SdkWorkflowInstanceRepository(
      WfInstanceSdkRepository instances,
      WfTaskSdkRepository tasks,
      WfActiveInstanceSdkRepository activePointers) {
    this.instances = Objects.requireNonNull(instances, "instances");
    this.tasks = Objects.requireNonNull(tasks, "tasks");
    this.activePointers = Objects.requireNonNull(activePointers, "activePointers");
  }

  // ====================== 实例 ======================

  @Override
  @Transactional
  public boolean claimAndSaveInstance(WorkflowInstance instance) {
    if (!claimActiveInstance(instance)) {
      return false;
    }
    saveInstance(instance, instance.startedBy());
    return true;
  }

  @Override
  public void saveInstance(WorkflowInstance instance, String operatorId) {
    Long tenantId = requireTenant();
    int updated =
        instances.updateInstance(
            instance.id(),
            tenantId,
            instance.entityCode(),
            instance.recordId(),
            instance.definitionKey(),
            instance.definitionVersion(),
            instance.currentNodeCode(),
            instance.phase().name(),
            instance.startedBy(),
            instance.startedAt(),
            instance.updatedAt(),
            operatorId);
    if (updated == 0) {
      instances.insertInstance(
          instance.id(),
          tenantId,
          instance.entityCode(),
          instance.recordId(),
          instance.definitionKey(),
          instance.definitionVersion(),
          instance.currentNodeCode(),
          instance.phase().name(),
          instance.startedBy(),
          instance.startedAt(),
          instance.updatedAt(),
          operatorId,
          Instant.now());
    }
  }

  @Override
  public Optional<WorkflowInstance> findInstance(String instanceId) {
    return instances.findInstanceRow(instanceId, requireTenant()).map(this::toInstance);
  }

  @Override
  public Optional<WorkflowInstance> findInstanceByRecord(String entityCode, String recordId) {
    return instances
        .findLatestInstanceRowByRecord(entityCode, recordId, requireTenant())
        .map(this::toInstance);
  }

  @Override
  public boolean compareAndSetPhase(
      String instanceId,
      WorkflowInstance.Phase expected,
      WorkflowInstance.Phase next,
      String operatorId) {
    int updated =
        instances.compareAndSetPhase(
            instanceId, requireTenant(), expected.name(), next.name(), Instant.now(), operatorId);
    if (updated == 0) {
      return false;
    }
    // 终态即释放活动槽位：否则上一次流程结束后，同一单据永久无法重新提交（陈旧指针把门焊死）。
    if (next == WorkflowInstance.Phase.APPROVED
        || next == WorkflowInstance.Phase.REJECTED
        || next == WorkflowInstance.Phase.WITHDRAWN
        || next == WorkflowInstance.Phase.TERMINATED) {
      activePointers.deleteActivePointerByInstance(requireTenant(), instanceId);
    }
    return true;
  }

  @Override
  public boolean claimActiveInstance(WorkflowInstance instance) {
    try {
      insertActivePointer(instance);
      return true;
    } catch (DuplicateKeyException ex) {
      // 已被占用：可能是他人真在启动，也可能是上一次流程终态后遗留的陈旧指针（释放失败 / 崩溃）。
      // 后者若不清理，会把重新提交永久焊死——故按指针指向实例的终态判定后清理重试一次。
      return reclaimIfStale(instance);
    }
  }

  @Override
  public void releaseActiveInstance(String entityCode, String recordId) {
    activePointers.deleteActivePointer(requireTenant(), entityCode, recordId);
  }

  private void insertActivePointer(WorkflowInstance instance) {
    activePointers.insertActivePointer(
        requireTenant(), instance.entityCode(), instance.recordId(), instance.id(), Instant.now());
  }

  private boolean reclaimIfStale(WorkflowInstance instance) {
    Optional<WorkflowInstance> holder =
        findInstanceByRecord(instance.entityCode(), instance.recordId());
    if (holder.isPresent() && !holder.get().isTerminated()) {
      return false; // 确有活动实例，不得抢占
    }
    int deleted =
        activePointers.deleteActivePointer(
            requireTenant(), instance.entityCode(), instance.recordId());
    if (deleted == 0) {
      return false;
    }
    try {
      insertActivePointer(instance);
      return true;
    } catch (DuplicateKeyException ex) {
      return false; // 清理后仍被他人抢到
    }
  }

  @Override
  public boolean compareAndAdvanceNode(
      String instanceId,
      String nodeCode,
      String nextNodeCode,
      WorkflowDefinition.ApprovalMode mode,
      Integer quorum,
      String operatorId) {
    // 关键：判据与推进在同一条 UPDATE 内完成（见 WfInstanceSdkRepository#compareAndAdvanceNode 的说明）。
    // 此处不得拆成「先查后改」。
    // mode == null（START / CONDITION / CC 等非审批节点）映射为显式 AUTO token——
    // 绝不能折成 SINGLE：那会要求「存在 APPROVED 任务」，非审批节点没有任务 ⇒ 0 行 ⇒ 流程启动即卡死。
    return instances.compareAndAdvanceNode(
            instanceId,
            requireTenant(),
            nodeCode,
            nextNodeCode,
            mode == null ? "AUTO" : mode.name(),
            quorum,
            Instant.now(),
            operatorId)
        > 0;
  }

  // ====================== 任务 ======================

  @Override
  public void saveTask(WorkflowTask task, String operatorId) {
    tasks.insertTask(
        task.id(),
        requireTenant(),
        task.instanceId(),
        task.nodeCode(),
        task.assigneeId(),
        task.status().name(),
        task.decision() == null ? null : task.decision().name(),
        task.comment(),
        task.dueAt(),
        Instant.now(),
        operatorId);
  }

  @Override
  public boolean saveTaskIfAbsent(WorkflowTask task, String operatorId) {
    // 条件插入：判据由数据库在同一条语句里算（不得拆成「先查后插」——见端口 javadoc 与
    // WfInstanceSdkRepository#insertTaskIfAbsent 的说明）。
    // 返回 0 行 = 已有同人同节点 PENDING，调用方据此跳过派单与通知。
    return tasks.insertTaskIfAbsent(
            task.id(),
            requireTenant(),
            task.instanceId(),
            task.nodeCode(),
            task.assigneeId(),
            task.status().name(),
            task.decision() == null ? null : task.decision().name(),
            task.comment(),
            task.dueAt(),
            Instant.now(),
            operatorId)
        > 0;
  }

  @Override
  public Optional<WorkflowTask> findTask(String taskId) {
    return tasks.findTaskRow(taskId, requireTenant()).map(this::toTask);
  }

  @Override
  public List<WorkflowTask> listPendingTasks(String instanceId) {
    return tasks.listPendingTaskRows(instanceId, requireTenant()).stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode) {
    return tasks.listTaskRowsByNode(instanceId, requireTenant(), nodeCode).stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public List<WorkflowTask> listTasksByNodeInCurrentRound(String instanceId, String nodeCode) {
    // 轮次由 SQL 侧 join 实例行取得，应用层不持有 node_round——避免「读到的轮次」与「推进时的轮次」两个真源。
    return tasks.listTaskRowsByNodeCurrentRound(instanceId, requireTenant(), nodeCode).stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public long registerRejectRound(String instanceId, String operatorId) {
    // UPDATE + SELECT 必须同一个事务（引擎已用 WorkflowUnitOfWork 包住 completeTask），否则并发驳回会读到旧值而少计。
    // 单条语句拿不到自增后的值（MySQL 无 RETURNING），故拆两步；两步之间的可见性由事务隔离级别保证。
    Long tenantId = requireTenant();
    int updated = instances.incrementRejectRounds(instanceId, tenantId, Instant.now(), operatorId);
    if (updated == 0) {
      throw new IllegalStateException("实例不存在，无法登记驳回轮次：" + instanceId);
    }
    Integer rounds = instances.findRejectRounds(instanceId, tenantId);
    return rounds == null ? 1L : rounds.longValue();
  }

  @Override
  public List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status) {
    return tasks.listTaskRowsByAssignee(assigneeId, requireTenant(), status.name()).stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public List<WorkflowTask> listPendingTasksByAssignee(String assigneeId, int limit, int offset) {
    // 分页与排序全部下推 SQL：应用层再排会让翻页出现重复/漏行（两处排序口径不可能永远一致）。
    return tasks
        .listPendingTaskRowsByAssigneePaged(
            assigneeId, requireTenant(), clampLimit(limit), clampOffset(offset))
        .stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public long countPendingTasksByAssignee(String assigneeId) {
    return tasks.countPendingTaskRowsByAssignee(assigneeId, requireTenant());
  }

  @Override
  public List<WorkflowTask> listDecidedTasksByAssignee(String assigneeId, int limit, int offset) {
    return tasks
        .listDecidedTaskRowsByAssigneePaged(
            assigneeId, requireTenant(), clampLimit(limit), clampOffset(offset))
        .stream()
        .map(this::toTask)
        .toList();
  }

  @Override
  public long countDecidedTasksByAssignee(String assigneeId) {
    return tasks.countDecidedTaskRowsByAssignee(assigneeId, requireTenant());
  }

  @Override
  public List<WorkflowInstance> listInstancesByPhase(WorkflowInstance.Phase phase, int limit) {
    return instances
        .listInstanceRowsByPhase(requireTenant(), phase.name(), clampLimit(limit))
        .stream()
        .map(this::toInstance)
        .toList();
  }

  /** 单页条数夹取（1..200）：分页参数来自 HTTP，不夹上限等于允许一次拉空整张表。 */
  private static int clampLimit(int limit) {
    return Math.min(Math.max(limit, 1), 200);
  }

  /** 偏移夹取（≥0）：负 offset 在 MySQL/H2 是语法错误，不夹即 500。 */
  private static int clampOffset(int offset) {
    return Math.max(offset, 0);
  }

  @Override
  public List<WorkflowTask> listTasks(String instanceId) {
    return tasks.listTaskRows(instanceId, requireTenant()).stream().map(this::toTask).toList();
  }

  @Override
  public List<String> findStuckInstances(Instant cutoff) {
    // 跨租户只读检测（看门狗）：不补 tenant_id 谓词，与中继的跨租户查询同口径（调度线程无 TenantContext）。
    // 仅返回 id 列表，不触达任何业务动作。SDK 行映射要求返回 Row 类，故在此抽取 id。
    return instances.findStuckInstanceIds(cutoff).stream().map(WfInstanceRow::getId).toList();
  }

  @Override
  public boolean compareAndSetTaskStatus(
      String taskId,
      WorkflowTask.Status expected,
      WorkflowTask.Status next,
      WorkflowTask.Decision decision,
      String comment,
      String operatorId) {
    Instant now = Instant.now();
    return tasks.compareAndSetTaskStatus(
            taskId,
            requireTenant(),
            expected.name(),
            next.name(),
            decision == null ? null : decision.name(),
            comment,
            now,
            now,
            operatorId)
        > 0;
  }

  @Override
  public boolean reassignTask(String taskId, String newAssigneeId, String operatorId) {
    return tasks.reassignTask(taskId, requireTenant(), newAssigneeId, Instant.now(), operatorId)
        > 0;
  }

  @Override
  public void cancelPendingTasks(String instanceId, String operatorId) {
    tasks.cancelPendingTasks(instanceId, requireTenant(), Instant.now(), operatorId);
  }

  @Override
  public void cancelPendingTasksOfNode(String instanceId, String nodeCode, String operatorId) {
    tasks.cancelPendingTasksOfNode(
        instanceId, requireTenant(), nodeCode, Instant.now(), operatorId);
  }

  // ====================== 内部 ======================

  /** 统一取 tenantId；缺失即失败关闭（引擎无租户概念，隔离不能因此失效）。 */
  private Long requireTenant() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("审批流桥接层缺少租户上下文（TenantContext）——拒绝在未知租户下读写 wf_* 数据");
    }
    return tenantId;
  }

  private WorkflowInstance toInstance(WfInstanceRow row) {
    return new WorkflowInstance(
        row.getId(),
        row.getEntityCode(),
        row.getRecordId(),
        row.getDefKey(),
        row.getDefVersion(),
        row.getCurrentNodeCode(),
        WorkflowInstance.Phase.valueOf(row.getPhase()),
        row.getStartedBy(),
        row.getStartedAt(),
        row.getUpdatedAt());
  }

  private WorkflowTask toTask(WfTaskRow row) {
    String decision = row.getDecision();
    return new WorkflowTask(
        row.getId(),
        row.getInstanceId(),
        row.getNodeCode(),
        row.getAssigneeId(),
        WorkflowTask.Status.valueOf(row.getStatus()),
        decision == null ? null : WorkflowTask.Decision.valueOf(decision),
        row.getComment(),
        row.getDueAt(),
        row.getCreatedAt(),
        row.getDecidedAt());
  }
}
