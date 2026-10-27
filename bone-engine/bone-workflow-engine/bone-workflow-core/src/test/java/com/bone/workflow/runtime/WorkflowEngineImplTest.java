package com.bone.workflow.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 审批流引擎运行时的判别力测试（内存 stub 端口，零外部依赖）。
 *
 * <p>覆盖：单签 / 会签(ALL) / 或签(ANY) / 驳回 / 条件分支 / 审批人解析不出则挂起 / 越权被拒 / 同结论幂等 / 仅发起人可撤回，以及四条 P0
 * 缺陷的回归：<b>会签并发死锁、终态被回写、重复发起产生双实例、挂起单据无法撤回</b>。
 */
class WorkflowEngineImplTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-10-22T00:00:00Z"), ZoneOffset.UTC);

  private final Map<String, WorkflowDefinition> definitions = new LinkedHashMap<>();
  private final Map<String, WorkflowInstance> instances = new LinkedHashMap<>();
  private final List<String> auditCalls = new ArrayList<>();
  private final Map<String, String> activeSlots = new LinkedHashMap<>();
  private final Map<String, WorkflowTask> tasks = new LinkedHashMap<>();
  private final List<String> statusCalls = new ArrayList<>();
  private final Map<String, Object> recordData = new LinkedHashMap<>();
  private final AtomicInteger idSeq = new AtomicInteger();
  private final List<String> taskCasOperators = new ArrayList<>();
  private final List<String> notifications = new ArrayList<>();
  private List<String> resolvedAssignees = List.of("mgr1");

  /**
   * 已决策状态显式集——与生产 SQL（{@code WfInstanceSdkRepository#compareAndAdvanceNode} 的 status NOT IN
   * 字面量）逐字一致。
   *
   * <p>刻意不用「非 PENDING」近似：那正是 v1 实现里的 fail-open 口径（v2 转办/委派状态会被当成已决策而静默推进）。
   */
  private static final Set<WorkflowTask.Status> DECIDED =
      Set.of(
          WorkflowTask.Status.APPROVED,
          WorkflowTask.Status.REJECTED,
          WorkflowTask.Status.CANCELLED);

  // ============================== 端口 stub ==============================

  private final WorkflowDefinitionRepository definitionRepository =
      new WorkflowDefinitionRepository() {
        @Override
        public Optional<WorkflowDefinition> findLatestPublished(String definitionKey) {
          return Optional.ofNullable(definitions.get(definitionKey));
        }

        @Override
        public Optional<WorkflowDefinition> findByVersion(String definitionKey, int version) {
          WorkflowDefinition definition = definitions.get(definitionKey);
          return definition != null && definition.version() == version
              ? Optional.of(definition)
              : Optional.empty();
        }

        @Override
        public List<WorkflowDefinition> listPublished(String definitionKey) {
          WorkflowDefinition definition = definitions.get(definitionKey);
          return definition == null ? List.of() : List.of(definition);
        }
      };

  private final WorkflowInstanceRepository instanceRepository =
      new WorkflowInstanceRepository() {
        @Override
        public void saveInstance(WorkflowInstance instance, String operatorId) {
          instances.put(instance.id(), instance);
        }

        @Override
        public boolean claimAndSaveInstance(WorkflowInstance instance) {
          if (!claimActiveInstance(instance)) {
            return false;
          }
          saveInstance(instance, instance.startedBy());
          return true;
        }

        @Override
        public boolean claimActiveInstance(WorkflowInstance instance) {
          // 测试 stub：模拟唯一键插入语义——已有未终结实例则抢占失败
          Optional<WorkflowInstance> existing =
              findInstanceByRecord(instance.entityCode(), instance.recordId());
          if (existing.isPresent() && !existing.get().isTerminated()) {
            return false;
          }
          activeSlots.put(instance.entityCode() + "|" + instance.recordId(), instance.id());
          return true;
        }

        @Override
        public void releaseActiveInstance(String entityCode, String recordId) {
          activeSlots.remove(entityCode + "|" + recordId);
        }

        @Override
        public Optional<WorkflowInstance> findInstance(String instanceId) {
          return Optional.ofNullable(instances.get(instanceId));
        }

        @Override
        public Optional<WorkflowInstance> findInstanceByRecord(String entityCode, String recordId) {
          return instances.values().stream()
              .filter(
                  instance ->
                      instance.entityCode().equals(entityCode)
                          && instance.recordId().equals(recordId))
              .findFirst();
        }

        @Override
        public void saveTask(WorkflowTask task, String operatorId) {
          tasks.put(task.id(), task);
        }

        /**
         * stub 版条件插入：与生产 SQL（{@code WfTaskSdkRepository#insertTaskIfAbsent}）同口径——已有同人同节点 PENDING
         * 即跳过。
         *
         * <p>stub 只证明引擎调用逻辑；「判据由数据库在同一条语句里算」的原子性由 {@code SdkWorkflowInstanceRepositoryH2Test} 真 SQL
         * 用例证明（内存 stub 没有并发语义）。
         */
        @Override
        public boolean saveTaskIfAbsent(WorkflowTask task, String operatorId) {
          boolean alreadyPending =
              tasks.values().stream()
                  .anyMatch(
                      t ->
                          t.instanceId().equals(task.instanceId())
                              && t.nodeCode().equals(task.nodeCode())
                              && t.assigneeId().equals(task.assigneeId())
                              && t.status() == WorkflowTask.Status.PENDING);
          if (alreadyPending) {
            return false;
          }
          tasks.put(task.id(), task);
          return true;
        }

        @Override
        public Optional<WorkflowTask> findTask(String taskId) {
          return Optional.ofNullable(tasks.get(taskId));
        }

        @Override
        public List<WorkflowTask> listPendingTasks(String instanceId) {
          return tasks.values().stream()
              .filter(
                  task ->
                      task.instanceId().equals(instanceId)
                          && task.status() == WorkflowTask.Status.PENDING)
              .toList();
        }

        @Override
        public List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode) {
          return tasks.values().stream()
              .filter(
                  task -> task.instanceId().equals(instanceId) && task.nodeCode().equals(nodeCode))
              .toList();
        }

        @Override
        public boolean compareAndSetTaskStatus(
            String taskId,
            WorkflowTask.Status expected,
            WorkflowTask.Status next,
            WorkflowTask.Decision decision,
            String comment,
            String operatorId) {
          taskCasOperators.add(operatorId);
          WorkflowTask task = tasks.get(taskId);
          if (task == null || task.status() != expected) {
            return false;
          }
          tasks.put(
              taskId,
              new WorkflowTask(
                  task.id(),
                  task.instanceId(),
                  task.nodeCode(),
                  task.assigneeId(),
                  next,
                  decision,
                  comment,
                  task.dueAt(),
                  task.createdAt(),
                  FIXED_CLOCK.instant()));
          return true;
        }

        /** 原子推进：判据（会签是否齐 / 是否有人驳回）与移动实例位置在同一次调用里完成——模拟存储层的条件 UPDATE。 */
        @Override
        public boolean compareAndAdvanceNode(
            String instanceId,
            String nodeCode,
            String nextNodeCode,
            WorkflowDefinition.ApprovalMode mode,
            String operatorId) {
          WorkflowInstance instance = instances.get(instanceId);
          if (instance == null || instance.isTerminated()) {
            return false;
          }
          if (!instance.currentNodeCode().equals(nodeCode)) {
            return false;
          }
          if (mode != null) {
            List<WorkflowTask> nodeTasks = listTasksByNode(instanceId, nodeCode);
            if (nodeTasks.isEmpty()) {
              return false;
            }
            // 单签（SINGLE）模式：同一节点同一访问仅 1 条任务，驳回已由 completeTask 兜底整单终止，
            // 此处再校验 REJECTED 会误伤「驳回退回重入」——历史访问留下的陈旧 REJECTED 任务不该阻断本次推进。
            // 会签（ALL）/或签（ANY）多办理人同访问：任一驳回即整单驳回，必须保留 REJECTED 守卫。
            if (mode != WorkflowDefinition.ApprovalMode.SINGLE
                && nodeTasks.stream()
                    .anyMatch(task -> task.status() == WorkflowTask.Status.REJECTED)) {
              return false;
            }
            // 与生产 SQL 同口径：会签 = 全部任务落在已决策显式集（不是「无 PENDING」）
            boolean complete =
                mode == WorkflowDefinition.ApprovalMode.ALL
                    ? nodeTasks.stream().allMatch(task -> DECIDED.contains(task.status()))
                    : nodeTasks.stream()
                        .anyMatch(task -> task.status() == WorkflowTask.Status.APPROVED);
            if (!complete) {
              return false;
            }
          }
          instances.put(
              instanceId,
              new WorkflowInstance(
                  instance.id(),
                  instance.entityCode(),
                  instance.recordId(),
                  instance.definitionKey(),
                  instance.definitionVersion(),
                  nextNodeCode,
                  instance.phase(),
                  instance.startedBy(),
                  instance.startedAt(),
                  instance.updatedAt()));
          return true;
        }

        @Override
        public boolean compareAndSetPhase(
            String instanceId,
            WorkflowInstance.Phase expected,
            WorkflowInstance.Phase next,
            String operatorId) {
          WorkflowInstance instance = instances.get(instanceId);
          if (instance == null || instance.phase() != expected) {
            return false;
          }
          instances.put(
              instanceId,
              new WorkflowInstance(
                  instance.id(),
                  instance.entityCode(),
                  instance.recordId(),
                  instance.definitionKey(),
                  instance.definitionVersion(),
                  instance.currentNodeCode(),
                  next,
                  instance.startedBy(),
                  instance.startedAt(),
                  FIXED_CLOCK.instant()));
          return true;
        }

        @Override
        public List<WorkflowTask> listTasksByAssignee(
            String assigneeId, WorkflowTask.Status status) {
          return tasks.values().stream()
              .filter(
                  task ->
                      task.assigneeId().equals(assigneeId)
                          && (status == null || task.status() == status))
              .toList();
        }

        @Override
        public List<WorkflowTask> listTasks(String instanceId) {
          return tasks.values().stream()
              .filter(task -> task.instanceId().equals(instanceId))
              .toList();
        }

        @Override
        public void cancelPendingTasks(String instanceId, String operatorId) {
          tasks.values().stream()
              .filter(
                  task ->
                      task.instanceId().equals(instanceId)
                          && task.status() == WorkflowTask.Status.PENDING)
              .toList()
              .forEach(task -> cancel(task));
        }

        @Override
        public boolean reassignTask(String taskId, String newAssigneeId, String operatorId) {
          WorkflowTask task = tasks.get(taskId);
          if (task == null || task.status() != WorkflowTask.Status.PENDING) {
            return false;
          }
          tasks.put(
              taskId,
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
                  task.decidedAt()));
          return true;
        }

        @Override
        public void cancelPendingTasksOfNode(
            String instanceId, String nodeCode, String operatorId) {
          tasks.values().stream()
              .filter(
                  task ->
                      task.instanceId().equals(instanceId)
                          && task.nodeCode().equals(nodeCode)
                          && task.status() == WorkflowTask.Status.PENDING)
              .toList()
              .forEach(task -> cancel(task));
        }

        private void cancel(WorkflowTask task) {
          tasks.put(
              task.id(),
              new WorkflowTask(
                  task.id(),
                  task.instanceId(),
                  task.nodeCode(),
                  task.assigneeId(),
                  WorkflowTask.Status.CANCELLED,
                  null,
                  null,
                  task.dueAt(),
                  task.createdAt(),
                  null));
        }
      };

  private final ApproverResolver approverResolver = (rule, node, context) -> resolvedAssignees;

  private final WorkflowRecordPort recordPort = (entityCode, recordId) -> recordData;

  private final WorkflowStatusPort statusPort =
      new WorkflowStatusPort() {
        @Override
        public void markApproved(
            String entityCode, String recordId, String instanceId, String operatorId) {
          statusCalls.add("APPROVED");
        }

        @Override
        public void markRejected(
            String entityCode,
            String recordId,
            String instanceId,
            String operatorId,
            String reason) {
          statusCalls.add("REJECTED");
        }

        @Override
        public void markWithdrawn(
            String entityCode, String recordId, String instanceId, String operatorId) {
          statusCalls.add("WITHDRAWN");
        }

        @Override
        public void markSuspended(
            String entityCode, String recordId, String instanceId, String reason) {
          statusCalls.add("SUSPENDED");
        }

        @Override
        public void markResumed(String entityCode, String recordId, String instanceId) {
          statusCalls.add("RESUMED");
        }

        @Override
        public void markTerminated(
            String entityCode,
            String recordId,
            String instanceId,
            String operatorId,
            String reason) {
          statusCalls.add("TERMINATED");
        }
      };

  private final WorkflowAuditPort auditPort =
      new WorkflowAuditPort() {
        @Override
        public void recordStart(WorkflowInstance instance, String operatorId) {}

        @Override
        public void recordAdvance(
            WorkflowInstance instance, String fromNode, String toNode, String branchLabel) {}

        @Override
        public void recordDecision(
            WorkflowTask task, WorkflowTask.Decision decision, String comment) {}

        @Override
        public void recordTermination(
            WorkflowInstance instance, String operatorId, String reason) {}

        @Override
        public void recordSuspension(WorkflowInstance instance, String reason) {}

        @Override
        public void recordResumption(WorkflowInstance instance, String operatorId) {}

        @Override
        public void recordReassignment(
            WorkflowTask task, String newAssigneeId, String operatorId, String reason) {
          auditCalls.add("REASSIGN:" + task.id() + "->" + newAssigneeId);
        }

        @Override
        public void recordUrge(WorkflowTask task, String operatorId, String comment) {
          auditCalls.add("URGE:" + task.id());
        }

        @Override
        public void recordTransfer(
            WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}

        @Override
        public void recordAddSign(
            WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}
      };

  private final WorkflowIdGenerator idGenerator = () -> "id-" + idSeq.incrementAndGet();

  /** 记录型通知端口：把每次通知记入 {@link #notifications}，供终态/派单通知用例断言。 */
  private final NotificationPort recordingNotificationPort =
      new NotificationPort() {
        @Override
        public void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance) {
          notifications.add("TASK_CREATED:" + task.assigneeId() + "@" + task.nodeCode());
        }

        @Override
        public void notifyUrged(
            WorkflowTask task, WorkflowInstance instance, String operatorId, String comment) {
          notifications.add("URGED:" + task.id());
        }

        @Override
        public void notifyReassigned(
            WorkflowTask task,
            WorkflowInstance instance,
            String newAssigneeId,
            String operatorId,
            String reason) {
          notifications.add("REASSIGNED:" + newAssigneeId);
        }

        @Override
        public void notifySuspended(WorkflowInstance instance, String reason) {
          notifications.add("SUSPENDED:" + instance.id());
        }

        @Override
        public void notifyTerminated(WorkflowInstance instance, String operatorId, String reason) {
          notifications.add("TERMINATED:" + instance.phase());
        }

        @Override
        public void notifyTaskCancelled(
            WorkflowTask task, WorkflowInstance instance, String reason) {
          notifications.add("TASK_CANCELLED:" + task.assigneeId() + "@" + task.nodeCode());
        }

        @Override
        public void notifyCc(
            WorkflowInstance instance, String nodeCode, java.util.List<String> recipients) {
          notifications.add("CC:" + String.join(",", recipients));
        }
      };

  private WorkflowEngineImpl newEngine() {
    return new WorkflowEngineImpl(
        definitionRepository,
        instanceRepository,
        approverResolver,
        recordPort,
        statusPort,
        auditPort,
        idGenerator,
        new StructuredConditionEvaluator(),
        new WorkflowDefinitionValidator(),
        FIXED_CLOCK,
        recordingNotificationPort);
  }

  // ============================== 定义构造 ==============================

  private static com.fasterxml.jackson.databind.JsonNode json(String text) {
    try {
      return MAPPER.readTree(text);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Test
  @DisplayName("管理员干预·改派：待办转给他人并留痕，已决策任务拒绝改派")
  void reassign_movesTaskAndAudits() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    String taskId = pendingTasks().get(0).id();
    assertEquals("mgr1", pendingTasks().get(0).assigneeId());

    WorkflowTask reassigned = engine.reassignTask(taskId, "mgr2", "admin", "原审批人休假");
    assertEquals("mgr2", reassigned.assigneeId());
    assertEquals(WorkflowTask.Status.PENDING, reassigned.status());
    assertTrue(auditCalls.contains("REASSIGN:" + taskId + "->mgr2"), "改派必须留痕");

    // 决策后不得改派：改派一个已办结的任务毫无意义
    engine.completeTask(taskId, WorkflowTask.Decision.APPROVE, "同意", "mgr2");
    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class, () -> engine.reassignTask(taskId, "mgr3", "admin", "x"));
    assertEquals(WorkflowErrorCode.ALREADY_DECIDED, error.code());
  }

  @Test
  @DisplayName("管理员干预·催办：只留痕与通知，不改变任务与流程状态")
  void urge_doesNotChangeState() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance started = engine.start("expense_reimbursement", "r1", "expense", "u1");
    String taskId = pendingTasks().get(0).id();

    WorkflowTask urged = engine.urgeTask(taskId, "admin", "请尽快处理");
    assertEquals(WorkflowTask.Status.PENDING, urged.status(), "催办不得改变任务状态");
    assertEquals(
        started.currentNodeCode(),
        engine.findInstance("expense_reimbursement", "r1").orElseThrow().currentNodeCode());
    assertTrue(auditCalls.contains("URGE:" + taskId), "催办必须留痕");
  }

  @Test
  @DisplayName("通知失败不阻断审批主链路")
  void notificationFailure_doesNotBreakApproval() {
    givenLinearSingleApproval();
    // 注入一个必然抛异常的通知端口：审批必须照常成功
    WorkflowEngineImpl engine =
        new WorkflowEngineImpl(
            definitionRepository,
            instanceRepository,
            approverResolver,
            recordPort,
            statusPort,
            auditPort,
            idGenerator,
            new StructuredConditionEvaluator(),
            new WorkflowDefinitionValidator(),
            FIXED_CLOCK,
            new NotificationPort() {
              @Override
              public void notifyTaskCreated(WorkflowTask task, WorkflowInstance instance) {
                throw new IllegalStateException("模拟通知网关故障");
              }

              @Override
              public void notifyUrged(
                  WorkflowTask task,
                  WorkflowInstance instance,
                  String operatorId,
                  String comment) {}

              @Override
              public void notifyReassigned(
                  WorkflowTask task,
                  WorkflowInstance instance,
                  String newAssigneeId,
                  String operatorId,
                  String reason) {}

              @Override
              public void notifySuspended(WorkflowInstance instance, String reason) {}

              @Override
              public void notifyTerminated(
                  WorkflowInstance instance, String operatorId, String reason) {}

              @Override
              public void notifyTaskCancelled(
                  WorkflowTask task, WorkflowInstance instance, String reason) {}

              @Override
              public void notifyCc(
                  WorkflowInstance instance, String nodeCode, java.util.List<String> recipients) {}
            });
    engine.start("expense_reimbursement", "r1", "expense", "u1");

    WorkflowInstance done =
        engine.completeTask(
            pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals(WorkflowInstance.Phase.APPROVED, done.phase(), "通知故障不得让审批失败");
  }

  private void givenLinearSingleApproval() {
    definitions.put(
        "expense",
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "mgr",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "主管"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "mgr", null, "提交"),
                new WorkflowDefinition.Edge("mgr", "end", null, "通过")),
            Map.of(
                "mgr", List.of(new WorkflowDefinition.AssignmentRule("LINE_MANAGER", Map.of())))));
  }

  private void givenApprovalWithMode(WorkflowDefinition.ApprovalMode mode) {
    definitions.put(
        "expense",
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "committee", WorkflowDefinition.NodeType.APPROVE, mode, "委员会"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "committee", null, "提交"),
                new WorkflowDefinition.Edge("committee", "end", null, "通过")),
            Map.of("committee", List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())))));
  }

  private void givenConditionalApproval() {
    definitions.put(
        "expense",
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "cond", WorkflowDefinition.NodeType.CONDITION, null, "金额分支"),
                new WorkflowDefinition.Node(
                    "big",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "总监"),
                new WorkflowDefinition.Node(
                    "small",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "主管"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "cond", null, "提交"),
                new WorkflowDefinition.Edge(
                    "cond",
                    "big",
                    json("{\"fieldCode\":\"amount\",\"operator\":\"gt\",\"value\":5000}"),
                    "大额"),
                new WorkflowDefinition.Edge("cond", "small", null, "默认"),
                new WorkflowDefinition.Edge("big", "end", null, "大额通过"),
                new WorkflowDefinition.Edge("small", "end", null, "常规通过")),
            Map.of(
                "big",
                List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())),
                "small",
                List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())))));
  }

  private List<WorkflowTask> pendingTasks() {
    return tasks.values().stream()
        .filter(task -> task.status() == WorkflowTask.Status.PENDING)
        .toList();
  }

  // ============================== 基础用例 ==============================

  @Test
  @DisplayName("单签：提交生成 1 条待办；同意后流程通过并回调状态端口")
  void singleApproval_completesFlow() {
    givenLinearSingleApproval();
    recordData.put("amount", 800);
    WorkflowEngineImpl engine = newEngine();

    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.RUNNING, instance.phase());
    assertEquals("mgr", instance.currentNodeCode());
    assertEquals(1, pendingTasks().size());

    String taskId = pendingTasks().get(0).id();
    WorkflowInstance done =
        engine.completeTask(taskId, WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals(WorkflowInstance.Phase.APPROVED, done.phase());
    assertEquals(List.of("APPROVED"), statusCalls);
  }

  @Test
  @DisplayName("会签（ALL）：一人同意不推进；第二人同意才推进（不得互相等待）")
  void countersign_requiresAll() {
    givenApprovalWithMode(WorkflowDefinition.ApprovalMode.ALL);
    resolvedAssignees = List.of("c1", "c2");
    WorkflowEngineImpl engine = newEngine();

    engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(2, pendingTasks().size());

    WorkflowInstance afterOne =
        engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c1");
    assertEquals(WorkflowInstance.Phase.RUNNING, afterOne.phase());
    assertTrue(statusCalls.isEmpty());

    WorkflowInstance afterAll =
        engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c2");
    assertEquals(WorkflowInstance.Phase.APPROVED, afterAll.phase());
    assertEquals(List.of("APPROVED"), statusCalls);
  }

  @Test
  @DisplayName("或签（ANY）：一人同意即推进；其余待办被作废（不得留幽灵待办）")
  void anyApproval_advancesAndCancelsOthers() {
    givenApprovalWithMode(WorkflowDefinition.ApprovalMode.ANY);
    resolvedAssignees = List.of("c1", "c2");
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(2, pendingTasks().size());

    WorkflowInstance done =
        engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c1");
    assertEquals(WorkflowInstance.Phase.APPROVED, done.phase());
    assertEquals(List.of("APPROVED"), statusCalls);
    assertTrue(pendingTasks().isEmpty(), "节点完成后余下待办必须作废，否则待办列表出现幽灵项");
  }

  @Test
  @DisplayName("驳回：流程终止为 REJECTED 并回调状态端口")
  void rejection_terminatesFlow() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    WorkflowInstance rejected =
        engine.completeTask(
            pendingTasks().get(0).id(), WorkflowTask.Decision.REJECT, "不符合政策", "mgr1");
    assertEquals(WorkflowInstance.Phase.REJECTED, rejected.phase());
    assertEquals(List.of("REJECTED"), statusCalls);
  }

  @Test
  @DisplayName("条件分支：金额超阈值派到大额节点，否则走默认节点")
  void conditionBranch_selectsNodeByData() {
    givenConditionalApproval();
    resolvedAssignees = List.of("bigboss");
    WorkflowEngineImpl engine = newEngine();

    recordData.put("amount", 6000);
    WorkflowInstance big = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals("big", big.currentNodeCode());

    recordData.put("amount", 100);
    WorkflowInstance small = engine.start("expense_reimbursement", "r2", "expense", "u1");
    assertEquals("small", small.currentNodeCode());
  }

  @Test
  @DisplayName("审批人解析不出 → 实例挂起 SUSPENDED，绝不静默放行")
  void unresolvableAssignee_suspendsInsteadOfPassing() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.SUSPENDED, instance.phase());
    assertTrue(pendingTasks().isEmpty());
    assertFalse(statusCalls.contains("APPROVED"), "挂起时不得回调「审批通过」");
    assertTrue(statusCalls.contains("SUSPENDED"), "挂起必须通知宿主（否则无人知晓的死单）");
  }

  @Test
  @DisplayName("越权处理他人任务 → 拒绝并给出稳定错误分类")
  void completingOthersTask_isRejected() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    String taskId = pendingTasks().get(0).id();
    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(taskId, WorkflowTask.Decision.APPROVE, "越权", "other"));
    assertEquals(WorkflowErrorCode.NOT_ASSIGNEE, error.code());
  }

  @Test
  @DisplayName("同结论重复提交 → 幂等，不重复推进（状态端口只被回调一次）")
  void repeatedSameDecision_isIdempotent() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    String taskId = pendingTasks().get(0).id();
    engine.completeTask(taskId, WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    engine.completeTask(taskId, WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals(List.of("APPROVED"), statusCalls);
  }

  @Test
  @DisplayName("撤回：仅发起人可撤回；撤回后流程终止且余下待办作废")
  void withdraw_onlyByInitiator() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class, () -> engine.withdraw(instance.id(), "someone"));
    assertEquals(WorkflowErrorCode.NOT_INITIATOR, error.code());
    WorkflowInstance withdrawn = engine.withdraw(instance.id(), "u1");
    assertEquals(WorkflowInstance.Phase.WITHDRAWN, withdrawn.phase());
    assertEquals(List.of("WITHDRAWN"), statusCalls);
    assertTrue(pendingTasks().isEmpty());
  }

  @Test
  @DisplayName("定义缺失 → 明确报错（不留空白实例）")
  void missingDefinition_failsFast() {
    WorkflowEngineImpl engine = newEngine();
    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.start("expense_reimbursement", "r1", "nope", "u1"));
    assertEquals(WorkflowErrorCode.DEFINITION_NOT_FOUND, error.code());
    assertNotNull(engine);
  }

  // ============================== P0 缺陷回归 ==============================

  @Test
  @DisplayName("P0 回归·重复发起：同一单据再次 start 复用同一实例（不得产生双实例双审批）")
  void start_isIdempotentForSameRecord() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance first = engine.start("expense_reimbursement", "r1", "expense", "u1");
    WorkflowInstance second = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(first.id(), second.id(), "重复发起必须复用实例");
    assertEquals(1, instances.size(), "不得为同一单据创建第二个实例");
    assertSame(first.id(), second.id());
  }

  @Test
  @DisplayName("P0 回归·终态不可回写：或签先同意后驳回，已通过的流程不得被改写成 REJECTED")
  void terminatedFlow_cannotBeRewritten() {
    givenApprovalWithMode(WorkflowDefinition.ApprovalMode.ANY);
    resolvedAssignees = List.of("c1", "c2");
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    List<WorkflowTask> pending = pendingTasks();
    engine.completeTask(pending.get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c1");
    assertEquals(WorkflowInstance.Phase.APPROVED, latestPhase());

    // c2 后到并驳回：必须被拒绝，流程保持 APPROVED
    String otherTaskId = pending.get(1).id();
    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(otherTaskId, WorkflowTask.Decision.REJECT, "驳回", "c2"));
    assertEquals(WorkflowErrorCode.INSTANCE_TERMINATED, error.code());
    assertEquals(WorkflowInstance.Phase.APPROVED, latestPhase());
    assertEquals(List.of("APPROVED"), statusCalls);
  }

  @Test
  @DisplayName("P0 回归·挂起可撤回：SUSPENDED 不是终态，发起人不得被锁死")
  void suspendedInstance_canStillBeWithdrawn() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.SUSPENDED, instance.phase());
    assertFalse(instance.isTerminated(), "挂起不是终态");

    WorkflowInstance withdrawn = engine.withdraw(instance.id(), "u1");
    assertEquals(WorkflowInstance.Phase.WITHDRAWN, withdrawn.phase());
    assertTrue(statusCalls.contains("WITHDRAWN"));
  }

  @Test
  @DisplayName("挂起恢复：组织架构修好后 resume 重新派单并续跑；仍解析不出则保持挂起并报错")
  void suspendedInstance_canBeResumedAfterFixingOrg() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of(); // 首次提交时组织架构断链 → 挂起
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.SUSPENDED, suspended.phase());
    assertTrue(pendingTasks().isEmpty(), "挂起时不得有幽灵待办");

    // 组织架构仍未修复：resume 必须明确失败，不得给「已恢复」的假象
    WorkflowEngineException stillBroken =
        assertThrows(WorkflowEngineException.class, () -> engine.resume(suspended.id(), "u1"));
    assertEquals(WorkflowErrorCode.NO_ASSIGNEE, stillBroken.code());
    assertEquals(
        WorkflowInstance.Phase.SUSPENDED,
        engine.findInstance("expense_reimbursement", "r1").orElseThrow().phase());

    // 修复组织架构后恢复
    resolvedAssignees = List.of("mgr1");
    WorkflowInstance resumed = engine.resume(suspended.id(), "u1");
    assertEquals(WorkflowInstance.Phase.RUNNING, resumed.phase());
    assertEquals("mgr", resumed.currentNodeCode());
    assertEquals(1, pendingTasks().size(), "恢复后应重新生成审批待办");
    assertTrue(statusCalls.contains("RESUMED"));

    // 续跑：审批通过 → 流程走完
    WorkflowInstance done =
        engine.completeTask(
            pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals(WorkflowInstance.Phase.APPROVED, done.phase());
  }

  @Test
  @DisplayName("恢复入口仅对挂起态有效：已通过的流程 resume 被拒")
  void resume_onTerminatedInstance_isRejected() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    WorkflowEngineException error =
        assertThrows(WorkflowEngineException.class, () -> engine.resume(instance.id(), "u1"));
    assertEquals(WorkflowErrorCode.INSTANCE_TERMINATED, error.code());
  }

  @Test
  @DisplayName("管理员干预·强制终止：进入 TERMINATED 终态、作废待办、可对账重投影")
  void terminateInstance_movesToTerminatedAndCancelsPending() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of("mgr1"); // 默认应能解析出审批人
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(1, pendingTasks().size(), "启动后应有 1 条待办");

    WorkflowInstance terminated = engine.terminateInstance(instance.id(), "admin", "错提单据");
    assertEquals(WorkflowInstance.Phase.TERMINATED, terminated.phase());
    assertTrue(terminated.isTerminated(), "TERMINATED 必须算终态");
    assertTrue(pendingTasks().isEmpty(), "终止必须作废余下待办，否则留下幽灵待办");
    assertTrue(statusCalls.contains("TERMINATED"), "终止必须回调状态端口（供业务单归档/锁定）");

    // 对账：被终止实例也应出现在「终态但缺投影」的扫描里（SELECT_TERMINAL_INSTANCES_MISSING_PROJECTION 已含 TERMINATED）
    assertFalse(engine.listInstanceHistory(instance.id()).isEmpty(), "终止后历史轨迹仍需可查");

    // 对已终结实例再终止：必须拒绝，不得覆盖
    WorkflowEngineException again =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.terminateInstance(instance.id(), "admin", "重复终止"));
    assertEquals(WorkflowErrorCode.INSTANCE_TERMINATED, again.code());
  }

  @Test
  @DisplayName("管理员干预·强制指派：SUSPENDED 且无任务时注入待办并恢复 RUNNING，闭合强制指派兜底缺口")
  void assignTask_onSuspendedInstance_injectsTaskAndResumes() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of(); // 审批人解析不出 ⇒ 挂起且无任务
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.SUSPENDED, suspended.phase());
    assertTrue(pendingTasks().isEmpty(), "挂起时根本无任务，reassignTask 也无从改派");

    // 管理员直接指定办理人兜底
    WorkflowInstance resumed =
        engine.assignTask(suspended.id(), null, "admin1", "admin", "主管离职，手动指派");
    assertEquals(WorkflowInstance.Phase.RUNNING, resumed.phase(), "注入任务后应恢复为 RUNNING");
    assertEquals(1, pendingTasks().size(), "应新建 1 条待办");
    assertEquals("admin1", pendingTasks().get(0).assigneeId());
    assertEquals("mgr", pendingTasks().get(0).nodeCode());
    assertTrue(statusCalls.contains("RESUMED"), "恢复必须回调状态端口");
    assertTrue(auditCalls.stream().anyMatch(a -> a.startsWith("REASSIGN:")), "强制指派必须留痕");

    // 幂等：相同 (node, assignee) 不重复建任务
    engine.assignTask(suspended.id(), null, "admin1", "admin", "再点一次");
    assertEquals(1, pendingTasks().size(), "同人同节点重复指派不得产生第二份待办");

    // 续跑：审批通过 → 流程走完
    WorkflowInstance done =
        engine.completeTask(
            pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "admin1");
    assertEquals(WorkflowInstance.Phase.APPROVED, done.phase());
  }

  @Test
  @DisplayName("强制指派并发双点：第二次指派被条件插入挡掉，不重复派单/通知")
  void assignTask_doubleClick_createsSingleTaskAndNotification() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");

    engine.assignTask(suspended.id(), null, "admin1", "admin", "指派");
    engine.assignTask(suspended.id(), null, "admin1", "admin", "重复指派（并发双点）");

    assertEquals(1, pendingTasks().size(), "同人同节点只能有一份待办");
    assertEquals(
        1,
        notifications.stream().filter(n -> n.startsWith("TASK_CREATED:")).count(),
        "第二次指派被数据库判据挡掉，不得再派单通知——否则办理人收到两份相同待办");
  }

  @Test
  @DisplayName("终态通知：审批通过 / 驳回 / 撤回 / 强制终止都必须通知发起人")
  void terminalPaths_notifyInitiator() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");

    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertTrue(
        notifications.contains("TERMINATED:APPROVED"), "审批通过必须通知发起人——只改状态不通知，发起人只能靠刷单据详情感知结果");

    // 驳回路径
    givenLinearSingleApproval();
    engine.start("expense_reimbursement", "r2", "expense", "u1");
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.REJECT, "不同意", "mgr1");
    assertTrue(notifications.contains("TERMINATED:REJECTED"), "驳回必须通知发起人");

    // 撤回路径
    givenLinearSingleApproval();
    WorkflowInstance r3 = engine.start("expense_reimbursement", "r3", "expense", "u1");
    engine.withdraw(r3.id(), "u1");
    assertTrue(notifications.contains("TERMINATED:WITHDRAWN"), "撤回必须通知发起人");

    // 强制终止路径
    givenLinearSingleApproval();
    WorkflowInstance r4 = engine.start("expense_reimbursement", "r4", "expense", "u1");
    engine.terminateInstance(r4.id(), "admin", "错提单据");
    assertTrue(notifications.contains("TERMINATED:TERMINATED"), "强制终止必须通知发起人");
  }

  @Test
  @DisplayName("恢复派单也发通知：resume 重建待办时办理人应收到待办提醒")
  void resume_notifiesTaskCreated() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of(); // 首次提交组织架构断链 → 挂起
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");
    notifications.clear();

    resolvedAssignees = List.of("mgr1");
    engine.resume(suspended.id(), "u1");

    assertTrue(
        notifications.contains("TASK_CREATED:mgr1@mgr"), "resume 重建待办必须通知办理人——否则挂起恢复后经办人仍靠刷列表才知情");
  }

  @Test
  @DisplayName("审批轨迹：listInstanceHistory 还原全量任务（含已决策），供详情页时间线")
  void listInstanceHistory_returnsAllTasksIncludingDecided() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of("mgr1");
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");

    List<WorkflowTask> before = engine.listInstanceHistory(instance.id());
    assertEquals(1, before.size());
    assertTrue(before.stream().allMatch(t -> t.status() == WorkflowTask.Status.PENDING));

    engine.completeTask(before.get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    List<WorkflowTask> after = engine.listInstanceHistory(instance.id());
    assertEquals(1, after.size());
    assertEquals(WorkflowTask.Status.APPROVED, after.get(0).status(), "轨迹须含已决策任务");
  }

  @Test
  @DisplayName("P0 回归·会签不死锁：两人同意后流程必然推进（原子判据，不得互等）")
  void countersign_neverDeadlocks() {
    givenApprovalWithMode(WorkflowDefinition.ApprovalMode.ALL);
    resolvedAssignees = List.of("c1", "c2");
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    List<WorkflowTask> pending = pendingTasks();
    engine.completeTask(pending.get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c1");
    engine.completeTask(pending.get(1).id(), WorkflowTask.Decision.APPROVE, "同意", "c2");
    assertEquals(WorkflowInstance.Phase.APPROVED, latestPhase());
  }

  @Test
  @DisplayName("审计归属：任务决策 CAS 的操作人必须是审批人本人（不得退化为租户 id / system）")
  void decisionCas_operatorIsApprover_notTenantOrSystem() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals(
        List.of("mgr1"),
        taskCasOperators,
        "compareAndSetTaskStatus 的 operatorId 必须是审批人本人——审计列写租户 id 或 system，" + "「谁批的」这个合规问题就永远答不出");
  }

  // ============================== 内控与可观测（2026-10-27） ==============================

  @Test
  @DisplayName("内控红线·自审批：审批人解析出发起人本人 ⇒ 挂起告警且不派单（不静默跳过审批层级）")
  void selfApproval_suspendsInsteadOfSkipping() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of("u1"); // 发起人本人
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");

    assertEquals(WorkflowInstance.Phase.SUSPENDED, suspended.phase());
    assertTrue(pendingTasks().isEmpty(), "自审批不得派单给发起人——否则这道审批事实上不存在");
    assertTrue(
        notifications.stream().anyMatch(each -> each.startsWith("SUSPENDED:")),
        "自审批必须告警管理员：静默放行会让「少一道审批」永远不被发现");
  }

  @Test
  @DisplayName("自审批挂起的恢复通道：指派给发起人被拒（SELF_APPROVAL），指派他人可续跑")
  void assignTask_toInitiator_isRejected() {
    givenLinearSingleApproval();
    resolvedAssignees = List.of("u1");
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance suspended = engine.start("expense_reimbursement", "r1", "expense", "u1");
    assertEquals(WorkflowInstance.Phase.SUSPENDED, suspended.phase());

    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.assignTask(suspended.id(), null, "u1", "admin", "指派给发起人本人"));
    assertEquals(WorkflowErrorCode.SELF_APPROVAL, error.code());

    // 恢复通道本身仍然可用：改派他人即可续跑（守卫不是为了把单据焊死）
    WorkflowInstance resumed = engine.assignTask(suspended.id(), null, "admin1", "admin", "改派他人");
    assertEquals(WorkflowInstance.Phase.RUNNING, resumed.phase());
    assertEquals(1, pendingTasks().size());
  }

  @Test
  @DisplayName("驳回必须填写意见：空白意见被拒（422），写明理由后可正常驳回")
  void reject_withoutComment_isRejected() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    WorkflowTask task = pendingTasks().get(0);

    WorkflowEngineException error =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "   ", "mgr1"));
    assertEquals(WorkflowErrorCode.REJECT_COMMENT_REQUIRED, error.code());

    WorkflowInstance rejected =
        engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "发票不合规，请重新开票", "mgr1");
    assertEquals(WorkflowInstance.Phase.REJECTED, rejected.phase());
  }

  @Test
  @DisplayName("撤回时其余办理人收到「待办作废」通知——待办不得凭空消失")
  void withdraw_notifiesCancelledApprovers() {
    givenLinearSingleApproval();
    WorkflowEngineImpl engine = newEngine();
    WorkflowInstance instance = engine.start("expense_reimbursement", "r1", "expense", "u1");
    notifications.clear();

    engine.withdraw(instance.id(), "u1");

    assertTrue(
        notifications.contains("TASK_CANCELLED:mgr1@mgr"), "被作废待办的办理人必须收到知会，否则他的待办凭空消失且无从得知单据去向");
    assertTrue(
        notifications.stream().anyMatch(each -> each.startsWith("TERMINATED:")), "发起人仍应收到终态通知");
  }

  @Test
  @DisplayName("或签一人决策后：其余办理人的待办被作废且收到知会（不留幽灵待办、不静默消失）")
  void anyMode_notifiesCancelledPeers() {
    givenApprovalWithMode(WorkflowDefinition.ApprovalMode.ANY);
    resolvedAssignees = List.of("c1", "c2");
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    List<WorkflowTask> pending = pendingTasks();
    notifications.clear();

    engine.completeTask(pending.get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "c1");

    WorkflowTask peer = pending.get(1);
    assertTrue(
        notifications.contains("TASK_CANCELLED:" + peer.assigneeId() + "@" + peer.nodeCode()),
        "或签下未决策的办理人待办被作废，必须收到知会：" + notifications);
    assertEquals(WorkflowInstance.Phase.APPROVED, latestPhase());
  }

  @Test
  @DisplayName("驳回退回指定节点：财务驳回退回主管，主管改派他人后续跑至通过")
  void rejectToNode_returnsToPreviousNodeAndResumes() {
    givenTwoLevelApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");

    // 第一级主管通过 → 推进到财务节点
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "同意", "mgr1");
    assertEquals("finance", latestInstance().currentNodeCode(), "应推进到财务节点");
    String financeTask = pendingTasks().get(0).id();

    // 财务节点办理人（本测试 stub 对所有规则返回 mgr1，故两节点办理人均为 mgr1）驳回，退回主管节点（而非整单驳回）
    WorkflowInstance rejected =
        engine.completeTask(financeTask, WorkflowTask.Decision.REJECT, "金额超标，请重核", "mgr1", "mgr");
    assertEquals(WorkflowInstance.Phase.RUNNING, rejected.phase(), "退回节点后流程仍在运行");
    assertEquals("mgr", rejected.currentNodeCode(), "currentNodeCode 应跳回主管节点");
    assertEquals(1, pendingTasks().size(), "主管节点应重新派单");
    assertEquals("mgr1", pendingTasks().get(0).assigneeId());

    // 主管重新审批通过 → 财务再审批 → 流程通过（退回通道闭环）
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "已重核", "mgr1");
    assertEquals("finance", latestInstance().currentNodeCode());
    engine.completeTask(pendingTasks().get(0).id(), WorkflowTask.Decision.APPROVE, "通过", "mgr1");
    assertEquals(WorkflowInstance.Phase.APPROVED, latestPhase());
  }

  @Test
  @DisplayName("驳回退回·目标非法（不存在/结束节点/当前节点）一律拒绝，绝不静默整单驳回")
  void rejectToNode_invalidTarget_rejected() {
    givenTwoLevelApproval();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");
    String mgrTask = pendingTasks().get(0).id();

    // 目标节点不存在
    WorkflowEngineException e1 =
        assertThrows(
            WorkflowEngineException.class,
            () ->
                engine.completeTask(mgrTask, WorkflowTask.Decision.REJECT, "原因", "mgr1", "ghost"));
    assertEquals(WorkflowErrorCode.REJECT_TARGET_NODE_INVALID, e1.code());

    // 目标为结束节点（退回即等于通过，语义错误）
    WorkflowEngineException e2 =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(mgrTask, WorkflowTask.Decision.REJECT, "原因", "mgr1", "end"));
    assertEquals(WorkflowErrorCode.REJECT_TARGET_NODE_INVALID, e2.code());

    // 目标为当前节点（自身循环无意义）
    WorkflowEngineException e3 =
        assertThrows(
            WorkflowEngineException.class,
            () -> engine.completeTask(mgrTask, WorkflowTask.Decision.REJECT, "原因", "mgr1", "mgr"));
    assertEquals(WorkflowErrorCode.REJECT_TARGET_NODE_INVALID, e3.code());

    // 非法退回不得改变流程状态、不得标记任务为已决策（否则留下死节点）
    assertEquals(WorkflowInstance.Phase.RUNNING, latestPhase(), "非法退回不得改变流程状态");
    assertEquals(WorkflowTask.Status.PENDING, pendingTasks().get(0).status(), "非法退回不得改动待办状态");
  }

  @Test
  @DisplayName("驳回退回·次数超限强制整单驳回（防 A 驳 B / B 驳 A 无限往返）")
  void rejectToNode_loopGuard_forcesTerminal() {
    // 与 WorkflowEngineImpl.MAX_REJECT_TO_NODE_ROUNDS 同值；该常量为引擎内控阈值，改值时本例需同步。
    final int REJECT_LIMIT = 10;
    givenStartNodeEnd();
    WorkflowEngineImpl engine = newEngine();
    engine.start("expense_reimbursement", "r1", "expense", "u1");

    // 反复「审批节点驳回退回发起节点」模拟 A↔B 拉锯：每轮留下 1 条 REJECTED 任务、流程仍运行
    for (int round = 1; round <= REJECT_LIMIT; round++) {
      WorkflowInstance after =
          engine.completeTask(
              pendingTasks().get(0).id(),
              WorkflowTask.Decision.REJECT,
              "退回重提 " + round,
              "mgr1",
              "start");
      assertEquals(
          WorkflowInstance.Phase.RUNNING, after.phase(), "第 " + round + " 轮退回仍应在运行（次数未达上限）");
      assertEquals(1, pendingTasks().size(), "每轮退回应重新派单");
    }
    // 第 (上限+1) 轮触发循环防护：强制整单驳回终止
    WorkflowInstance terminal =
        engine.completeTask(
            pendingTasks().get(0).id(), WorkflowTask.Decision.REJECT, "退回重提超限", "mgr1", "start");
    assertEquals(WorkflowInstance.Phase.REJECTED, terminal.phase(), "次数超限应强制整单驳回终止");
  }

  private WorkflowInstance latestInstance() {
    return instances.values().iterator().next();
  }

  private void givenTwoLevelApproval() {
    definitions.put(
        "expense",
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "mgr",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "主管"),
                new WorkflowDefinition.Node(
                    "finance",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "财务"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "mgr", null, "提交"),
                new WorkflowDefinition.Edge("mgr", "finance", null, "主管通过"),
                new WorkflowDefinition.Edge("finance", "end", null, "财务通过")),
            Map.of(
                "mgr", List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())),
                "finance", List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())))));
  }

  private void givenStartNodeEnd() {
    definitions.put(
        "expense",
        new WorkflowDefinition(
            "expense",
            1,
            List.of(
                new WorkflowDefinition.Node("start", WorkflowDefinition.NodeType.START, null, "发起"),
                new WorkflowDefinition.Node(
                    "node",
                    WorkflowDefinition.NodeType.APPROVE,
                    WorkflowDefinition.ApprovalMode.SINGLE,
                    "审批"),
                new WorkflowDefinition.Node("end", WorkflowDefinition.NodeType.END, null, "结束")),
            List.of(
                new WorkflowDefinition.Edge("start", "node", null, "提交"),
                new WorkflowDefinition.Edge("node", "end", null, "通过")),
            Map.of("node", List.of(new WorkflowDefinition.AssignmentRule("ROLE", Map.of())))));
  }

  private WorkflowInstance.Phase latestPhase() {
    return instances.values().iterator().next().phase();
  }
}
