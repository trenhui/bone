package com.bone.app.purchase.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowInstance;
import com.bone.workflow.domain.WorkflowTask;
import com.bone.workflow.ports.spi.WorkflowAuditPort;
import com.bone.workflow.ports.spi.WorkflowDefinitionRepository;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.ports.spi.WorkflowInstanceRepository;
import com.bone.workflow.ports.spi.WorkflowRecordPort;
import com.bone.workflow.ports.spi.WorkflowStatusPort;
import com.bone.workflow.runtime.WorkflowEngineImpl;
import com.bone.workflow.starter.adapter.RecordFieldApproverResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>0068 迁移的流程定义种子 JSON → 静态校验 + 真引擎领域行为</b>的回归测试（与报销 {@code ExpenseWorkflowDefinitionTest}
 * 对等，把"采购引用工作流引擎"的验证从装配正确性提升到真引擎跑通）。
 *
 * <p><b>为什么必须有这一层</b>：0068 的种子 JSON 是手写在迁移脚本里的一行长文本——形状错了（节点/边/assignments 键名、FIELD
 * 指派字段名）在单测里永远发现不了，只有引擎加载定义时才炸（且是生产 submit 时刻才炸）。本测试<b>直接从 0068 迁移文件解析出
 * definition_json</b>（单一真源，测试与上线种子零漂移），分两层验证：
 *
 * <ul>
 *   <li><b>结构层</b>：{@link WorkflowDefinitionValidator} 静态校验 + 节点/边/指派结构断言；
 *   <li><b>行为层</b>：真 {@link WorkflowEngineImpl} + 内存端口替身 + <b>生产默认指派解析器</b> {@link
 *       RecordFieldApproverResolver}（{@code RoleMemberProvider=null}，与当前组织目录未接入的生产形态一致）+ 记录替身
 *       状态端口，跑单级审批分支、FIELD 指派、挂起 fail-closed、审批/驳回终态、重复启动幂等。
 * </ul>
 *
 * <p><b>与 starter 自带 H2 回归的分工</b>：starter 的 {@code WorkflowEngineWiringH2Test} 覆盖「引擎经真实 JDBC/SDK
 * 仓储的原子推进与会签」（持久化侧）；本测试覆盖「<b>采购这份定义</b>的形状与领域行为」。
 */
class PurchaseWorkflowDefinitionTest {

  private static final String MIGRATION = "../../scripts/migration/0068_purchase_workflow_seed.sql";
  private static final String DEF_KEY = "purchase_order";
  private static final String ENTITY = "purchase_order";

  private final ObjectMapper mapper = new ObjectMapper();
  private final AtomicInteger idSeq = new AtomicInteger();
  private final Map<String, Map<String, Object>> records = new HashMap<>();
  private final List<RecordedStatus> statusCalls = new ArrayList<>();
  private WorkflowEngine engine;
  private WorkflowDefinition definition;

  @BeforeEach
  void setUp() throws Exception {
    TenantContext.setTenantId(1001L);
    definition = loadSeededDefinition();

    InMemoryInstanceRepository instances = new InMemoryInstanceRepository();
    engine =
        new WorkflowEngineImpl(
            new SeededDefinitionRepository(definition),
            instances,
            new RecordFieldApproverResolver(null), // 组织目录未接入：ROLE 策略解析不出
            recordPort(),
            recordingStatusPort(),
            auditPort(),
            idGenerator(),
            new StructuredConditionEvaluator(),
            new WorkflowDefinitionValidator(),
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  // ====================== 结构层：种子 JSON 的静态校验 ======================

  @Test
  @DisplayName("种子定义通过 WorkflowDefinitionValidator 静态校验（形状错在加载期即炸，不带到生产）")
  void seedDefinition_passesStaticValidation() {
    List<WorkflowDefinitionValidator.Violation> violations =
        new WorkflowDefinitionValidator().validate(definition);
    assertThat(violations).as("0068 种子定义必须通过静态校验；违规即上线后 submit 时刻才炸的隐患").isEmpty();
  }

  @Test
  @DisplayName("节点/边/指派结构与 0068 设计一致（单级审批：start → approve → end）")
  void seedDefinition_structureMatchesSpec() {
    assertThat(definition.key()).isEqualTo(DEF_KEY);
    assertThat(definition.version()).isEqualTo(1);
    assertThat(definition.nodes()).hasSize(3);
    assertThat(definition.edges()).hasSize(2);

    // 起点恰一条出边，审批节点恰一条无条件出边（到 END）
    assertThat(definition.edges().stream().filter(e -> e.from().equals("start")).toList())
        .hasSize(1);
    assertThat(definition.edges().stream().filter(e -> e.from().equals("approve")).toList())
        .hasSize(1);
    assertThat(
            definition.edges().stream()
                .filter(e -> e.from().equals("approve"))
                .findFirst()
                .orElseThrow())
        .extracting(e -> e.to())
        .isEqualTo("end");

    // 指派规则：approve 节点挂 FIELD approver_id
    assertThat(definition.assignments().keySet()).containsExactlyInAnyOrder("approve");
    assertThat(definition.assignments().get("approve")).hasSize(1);
    assertThat(definition.assignments().get("approve").get(0).strategy()).isEqualTo("FIELD");
    assertThat(definition.assignments().get("approve").get(0).params())
        .containsEntry("fieldCode", "approver_id");
  }

  // ====================== 行为层：真引擎驱动这份定义 ======================

  @Test
  @DisplayName("submit → approve 节点，办理人 = 表单 approver_id（FIELD 指派）")
  void submit_routesToApprove_withAssignee() {
    WorkflowInstance instance = start("r-1", Map.of("approver_id", "buyer-01"));

    assertThat(instance.currentNodeCode()).isEqualTo("approve");
    assertThat(instance.phase()).isEqualTo(WorkflowInstance.Phase.RUNNING);
    assertThat(engine.listPendingTasks(instance.id())).hasSize(1);
    assertThat(engine.listPendingTasks(instance.id()).get(0).assigneeId()).isEqualTo("buyer-01");
  }

  @Test
  @DisplayName("approver_id 缺失且组织目录未接入（FIELD 落空）→ 挂起，不放行、不静默跳过")
  void noAssignee_suspends() {
    WorkflowInstance instance = start("r-susp", Map.of());

    assertThat(instance.isSuspended()).isTrue();
    assertThat(engine.listPendingTasks(instance.id())).isEmpty();
    // 挂起必须留痕（运维可见）且不落任何终态投影
    assertThat(statusCalls).anySatisfy(s -> assertThat(s.intent()).isEqualTo("SUSPENDED"));
    assertThat(statusCalls).noneSatisfy(s -> assertThat(s.intent()).isIn("APPROVED", "REJECTED"));
  }

  @Test
  @DisplayName("审批通过 → 实例 APPROVED（approve→end 后抵达 END 节点）；状态端口只留痕")
  void approve_reachesApproved_andPortRecordsOnly() {
    WorkflowInstance instance = start("r-approve", Map.of("approver_id", "buyer-01"));
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    WorkflowInstance after =
        engine.completeTask(task.id(), WorkflowTask.Decision.APPROVE, "同意", "buyer-01");

    assertThat(after.phase()).isEqualTo(WorkflowInstance.Phase.APPROVED);
    assertThat(after.currentNodeCode()).isEqualTo("end");
    assertThat(engine.listPendingTasks(after.id())).isEmpty();
    assertThat(statusCalls).hasSize(1);
    assertThat(statusCalls.get(0).intent()).isEqualTo("APPROVED");
  }

  @Test
  @DisplayName("驳回 → 实例 REJECTED；驳回原因随意图留痕")
  void reject_reachesRejected_withReason() {
    WorkflowInstance instance = start("r-reject", Map.of("approver_id", "buyer-01"));
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    WorkflowInstance after =
        engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "预算不足", "buyer-01");

    assertThat(after.phase()).isEqualTo(WorkflowInstance.Phase.REJECTED);
    assertThat(statusCalls).hasSize(1);
    assertThat(statusCalls.get(0).intent()).isEqualTo("REJECTED");
    assertThat(statusCalls.get(0).params()).isEqualTo("预算不足");
  }

  @Test
  @DisplayName("重复 start（连点/重试）→ 复用既有实例，不产生双份审批")
  void repeatedStart_reusesInstance() {
    WorkflowInstance first = start("r-idem", Map.of("approver_id", "buyer-01"));
    WorkflowInstance second = start("r-idem", Map.of("approver_id", "buyer-01"));

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(engine.listPendingTasks(first.id())).hasSize(1);
  }

  @Test
  @DisplayName("被驳回后重提（reopen→DRAFT→submit）→ 新实例走当前定义（版本快照语义）")
  void resubmitAfterReject_createsNewInstance() {
    WorkflowInstance first = start("r-reject2", Map.of("approver_id", "buyer-01"));
    WorkflowTask task = engine.listPendingTasks(first.id()).get(0);
    engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "预算不足", "buyer-01");

    WorkflowInstance second = start("r-reject2", Map.of("approver_id", "buyer-01"));

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(second.phase()).isEqualTo(WorkflowInstance.Phase.RUNNING);
  }

  // ====================== 夹具 ======================

  private WorkflowInstance start(String recordId, Map<String, Object> data) {
    records.put(recordId, data);
    return engine.start(ENTITY, recordId, DEF_KEY, "u-applicant");
  }

  private WorkflowRecordPort recordPort() {
    return (entityCode, recordId) -> records.getOrDefault(recordId, Map.of());
  }

  private WorkflowIdGenerator idGenerator() {
    return () -> "W" + idSeq.incrementAndGet();
  }

  private WorkflowStatusPort recordingStatusPort() {
    return new WorkflowStatusPort() {
      @Override
      public void markApproved(
          String entityCode, String recordId, String instanceId, String operatorId) {
        statusCalls.add(new RecordedStatus("APPROVED", null));
      }

      @Override
      public void markRejected(
          String entityCode, String recordId, String instanceId, String operatorId, String reason) {
        statusCalls.add(new RecordedStatus("REJECTED", reason));
      }

      @Override
      public void markWithdrawn(
          String entityCode, String recordId, String instanceId, String operatorId) {
        statusCalls.add(new RecordedStatus("WITHDRAWN", null));
      }

      @Override
      public void markSuspended(
          String entityCode, String recordId, String instanceId, String reason) {
        statusCalls.add(new RecordedStatus("SUSPENDED", reason));
      }

      @Override
      public void markResumed(String entityCode, String recordId, String instanceId) {
        statusCalls.add(new RecordedStatus("RESUMED", null));
      }

      @Override
      public void markTerminated(
          String entityCode, String recordId, String instanceId, String operatorId, String reason) {
        statusCalls.add(new RecordedStatus("TERMINATED", reason));
      }
    };
  }

  /** 状态投影调用记录（intent + 参数）。 */
  private record RecordedStatus(String intent, String params) {}

  private WorkflowAuditPort auditPort() {
    return new WorkflowAuditPort() {
      @Override
      public void recordStart(WorkflowInstance instance, String operatorId) {}

      @Override
      public void recordAdvance(
          WorkflowInstance instance, String fromNode, String toNode, String branchLabel) {}

      @Override
      public void recordDecision(
          WorkflowTask task, WorkflowTask.Decision decision, String comment) {}

      @Override
      public void recordTermination(WorkflowInstance instance, String operatorId, String reason) {}

      @Override
      public void recordSuspension(WorkflowInstance instance, String reason) {}

      @Override
      public void recordResumption(WorkflowInstance instance, String operatorId) {}

      @Override
      public void recordReassignment(
          WorkflowTask task, String newAssigneeId, String operatorId, String reason) {}

      @Override
      public void recordUrge(WorkflowTask task, String operatorId, String comment) {}

      @Override
      public void recordTransfer(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}

      @Override
      public void recordAddSign(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}
    };
  }

  /** 定义仓储替身：只返回从 0068 种子解析的定义。 */
  private record SeededDefinitionRepository(WorkflowDefinition definition)
      implements WorkflowDefinitionRepository {
    @Override
    public Optional<WorkflowDefinition> findLatestPublished(String definitionKey) {
      return definition.key().equals(definitionKey) ? Optional.of(definition) : Optional.empty();
    }

    @Override
    public Optional<WorkflowDefinition> findByVersion(String definitionKey, int version) {
      return findLatestPublished(definitionKey).filter(d -> d.version() == version);
    }

    @Override
    public List<WorkflowDefinition> listPublished(String definitionKey) {
      return findLatestPublished(definitionKey).map(List::of).orElse(List.of());
    }
  }

  /**
   * 从 0068 迁移脚本解析 definition_json——<b>测试与上线种子同源</b>。
   *
   * <p>surefire 工作目录 = 模块基目录（bone-apps/bone-app-purchase），故迁移脚本在两级之上。解析失败（文件被移动/JSON
   * 行被重排）时测试直接失败——不允许静默跳过，否则「测试绿、上线种子坏」。
   */
  private WorkflowDefinition loadSeededDefinition() throws IOException {
    Path migration = Paths.get(MIGRATION);
    assertThat(Files.exists(migration))
        .as("0068 迁移脚本必须存在（测试与上线种子同源的锚点）: %s", migration.toAbsolutePath())
        .isTrue();
    String json = null;
    for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
      int idx = line.indexOf("'{\"key\":\"purchase_order\"");
      if (idx >= 0) {
        int start = line.indexOf('\'', idx) + 1;
        int end = line.indexOf('\'', start);
        json = line.substring(start, end);
        break;
      }
    }
    assertThat(json).as("0068 必须含 purchase_order 的 definition_json 种子行").isNotNull();
    return mapper.readValue(json, WorkflowDefinition.class);
  }

  /** 内存实例仓储替身（覆盖 WorkflowInstanceRepository 接口全部方法，仅驱动引擎领域逻辑）。 */
  /**
   * 内存实例仓储替身：复刻生产仓储并发口径（CAS / saveTaskIfAbsent / claimActiveInstance / compareAndAdvanceNode）的内存实现。
   */
  private static final class InMemoryInstanceRepository implements WorkflowInstanceRepository {

    private final Map<String, WorkflowInstance> instances = new HashMap<>();
    private final Map<String, String> activeSlots = new HashMap<>();
    private final List<WorkflowTask> tasks = new ArrayList<>();

    private static String slotKey(String entityCode, String recordId) {
      return entityCode + "|" + recordId;
    }

    @Override
    public void saveInstance(WorkflowInstance instance, String operatorId) {
      instances.put(instance.id(), instance);
    }

    @Override
    public boolean claimAndSaveInstance(WorkflowInstance instance) {
      if (!claimActiveInstance(instance)) {
        return false;
      }
      saveInstance(instance, null);
      return true;
    }

    @Override
    public boolean claimActiveInstance(WorkflowInstance instance) {
      String key = slotKey(instance.entityCode(), instance.recordId());
      String existing = activeSlots.get(key);
      if (existing != null) {
        WorkflowInstance prev = instances.get(existing);
        if (prev != null && prev.isTerminated()) {
          activeSlots.remove(key);
        } else {
          return false;
        }
      }
      activeSlots.put(key, instance.id());
      return true;
    }

    @Override
    public void releaseActiveInstance(String entityCode, String recordId) {
      activeSlots.remove(slotKey(entityCode, recordId));
    }

    @Override
    public Optional<WorkflowInstance> findInstance(String instanceId) {
      return Optional.ofNullable(instances.get(instanceId));
    }

    @Override
    public Optional<WorkflowInstance> findInstanceByRecord(String entityCode, String recordId) {
      return instances.values().stream()
          .filter(i -> i.entityCode().equals(entityCode) && i.recordId().equals(recordId))
          .max(java.util.Comparator.comparing(WorkflowInstance::id));
    }

    @Override
    public void saveTask(WorkflowTask task, String operatorId) {
      tasks.add(task);
    }

    @Override
    public boolean saveTaskIfAbsent(WorkflowTask task, String operatorId) {
      boolean duplicate =
          tasks.stream()
              .anyMatch(
                  t ->
                      t.instanceId().equals(task.instanceId())
                          && t.nodeCode().equals(task.nodeCode())
                          && t.assigneeId().equals(task.assigneeId())
                          && t.status() == WorkflowTask.Status.PENDING);
      if (duplicate) {
        return false;
      }
      tasks.add(task);
      return true;
    }

    @Override
    public Optional<WorkflowTask> findTask(String taskId) {
      return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst();
    }

    @Override
    public List<WorkflowTask> listPendingTasks(String instanceId) {
      return tasks.stream()
          .filter(
              t -> t.instanceId().equals(instanceId) && t.status() == WorkflowTask.Status.PENDING)
          .toList();
    }

    @Override
    public List<WorkflowTask> listTasksByNode(String instanceId, String nodeCode) {
      return tasks.stream()
          .filter(t -> t.instanceId().equals(instanceId) && t.nodeCode().equals(nodeCode))
          .toList();
    }

    @Override
    public List<WorkflowTask> listTasksByNodeInCurrentRound(String instanceId, String nodeCode) {
      return listTasksByNode(instanceId, nodeCode);
    }

    @Override
    public List<WorkflowTask> listTasksByAssignee(String assigneeId, WorkflowTask.Status status) {
      return tasks.stream()
          .filter(t -> t.assigneeId().equals(assigneeId) && t.status() == status)
          .toList();
    }

    @Override
    public List<WorkflowTask> listTasks(String instanceId) {
      return tasks.stream().filter(t -> t.instanceId().equals(instanceId)).toList();
    }

    private final Map<String, Long> rejectRounds = new HashMap<>();

    @Override
    public long registerRejectRound(String instanceId, String operatorId) {
      long next = rejectRounds.getOrDefault(instanceId, 0L) + 1;
      rejectRounds.put(instanceId, next);
      return next;
    }

    @Override
    public boolean compareAndSetTaskStatus(
        String taskId,
        WorkflowTask.Status expected,
        WorkflowTask.Status next,
        WorkflowTask.Decision decision,
        String comment,
        String operatorId) {
      Optional<WorkflowTask> found = findTask(taskId);
      if (found.isEmpty() || found.get().status() != expected) {
        return false;
      }
      WorkflowTask old = found.get();
      WorkflowInstance instance = instances.get(old.instanceId());
      if (instance == null || instance.phase() != WorkflowInstance.Phase.RUNNING) {
        return false;
      }
      tasks.remove(old);
      tasks.add(
          new WorkflowTask(
              old.id(),
              old.instanceId(),
              old.nodeCode(),
              old.assigneeId(),
              next,
              decision,
              comment,
              old.dueAt(),
              old.createdAt(),
              java.time.Instant.now()));
      return true;
    }

    @Override
    public boolean compareAndAdvanceNode(
        String instanceId,
        String nodeCode,
        String nextNodeCode,
        WorkflowDefinition.ApprovalMode mode,
        Integer quorum,
        String operatorId) {
      WorkflowInstance instance = instances.get(instanceId);
      if (instance == null
          || !instance.currentNodeCode().equals(nodeCode)
          || instance.phase() != WorkflowInstance.Phase.RUNNING) {
        return false;
      }
      List<WorkflowTask> nodeTasks = listTasksByNode(instanceId, nodeCode);
      if (nodeTasks.stream().anyMatch(t -> t.status() == WorkflowTask.Status.REJECTED)) {
        return false;
      }
      boolean satisfied;
      if (mode == null) {
        satisfied = true;
      } else if (mode == WorkflowDefinition.ApprovalMode.ALL) {
        long done =
            nodeTasks.stream()
                .filter(
                    t ->
                        t.status() == WorkflowTask.Status.APPROVED
                            || t.status() == WorkflowTask.Status.REJECTED
                            || t.status() == WorkflowTask.Status.CANCELLED)
                .count();
        satisfied =
            !nodeTasks.isEmpty()
                && (quorum == null || quorum <= 0 ? nodeTasks.size() == done : done >= quorum);
      } else if (mode == WorkflowDefinition.ApprovalMode.RATIO) {
        long approved =
            nodeTasks.stream().filter(t -> t.status() == WorkflowTask.Status.APPROVED).count();
        satisfied =
            !nodeTasks.isEmpty()
                && quorum != null
                && quorum > 0
                && approved * 100 >= quorum * nodeTasks.size();
      } else {
        satisfied = nodeTasks.stream().anyMatch(t -> t.status() == WorkflowTask.Status.APPROVED);
      }
      if (!satisfied) {
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
              nextNodeCode,
              instance.phase(),
              instance.startedBy(),
              instance.startedAt(),
              java.time.Instant.now()));
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
              java.time.Instant.now()));
      if (next != WorkflowInstance.Phase.RUNNING && next != WorkflowInstance.Phase.SUSPENDED) {
        releaseActiveInstance(instance.entityCode(), instance.recordId());
      }
      return true;
    }

    @Override
    public boolean reassignTask(String taskId, String newAssigneeId, String operatorId) {
      Optional<WorkflowTask> found = findTask(taskId);
      if (found.isEmpty() || found.get().status() != WorkflowTask.Status.PENDING) {
        return false;
      }
      WorkflowTask old = found.get();
      tasks.remove(old);
      tasks.add(
          new WorkflowTask(
              old.id(),
              old.instanceId(),
              old.nodeCode(),
              newAssigneeId,
              old.status(),
              old.decision(),
              old.comment(),
              old.dueAt(),
              old.createdAt(),
              old.decidedAt()));
      return true;
    }

    @Override
    public void cancelPendingTasks(String instanceId, String operatorId) {
      tasks.removeIf(
          t -> t.instanceId().equals(instanceId) && t.status() == WorkflowTask.Status.PENDING);
    }

    @Override
    public void cancelPendingTasksOfNode(String instanceId, String nodeCode, String operatorId) {
      tasks.removeIf(
          t ->
              t.instanceId().equals(instanceId)
                  && t.nodeCode().equals(nodeCode)
                  && t.status() == WorkflowTask.Status.PENDING);
    }
  }
}
