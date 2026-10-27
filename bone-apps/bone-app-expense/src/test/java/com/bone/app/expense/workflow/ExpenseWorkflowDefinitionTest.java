package com.bone.app.expense.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.StructuredConditionEvaluator;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowEngine;
import com.bone.workflow.domain.WorkflowEngineException;
import com.bone.workflow.domain.WorkflowErrorCode;
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
import java.time.Instant;
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
 * <b>0057 迁移的流程定义种子 JSON → 静态校验 + 真引擎领域行为 + 真实状态投影端口</b>的回归测试。
 *
 * <p><b>为什么必须有这一层</b>：种子 JSON 是手写在迁移脚本里的一行长文本——形状错了（节点/边/assignments 键名、{@code when}
 * 结构、默认边末位、档位阈值）在单测里永远发现不了，只有引擎加载定义时才炸（且是生产 submit 时刻才炸）。本测试<b>直接从 0057 迁移文件解析出
 * definition_json</b>（单一真源，测试与上线种子零漂移），分两层验证：
 *
 * <ul>
 *   <li><b>结构层</b>：{@link WorkflowDefinitionValidator} 静态校验 + 档位阈值/默认边末位等结构断言；
 *   <li><b>行为层</b>：真 {@link WorkflowEngineImpl} + 内存端口替身 + <b>生产默认指派解析器</b> {@link
 *       RecordFieldApproverResolver}（{@code RoleMemberProvider=null}，与当前组织目录未接入的生产形态一致）+ <b>真实</b>
 *       {@link ExpenseWorkflowStatusPort}，跑三档金额链式分支、union 指派、挂起 fail-closed、审批/驳回/撤回终态、
 *       迟到审批拒绝、重复启动幂等。
 * </ul>
 *
 * <p><b>与 starter 自带 H2 回归的分工</b>：starter 的 {@code WorkflowEngineWiringH2Test} 覆盖「引擎经真实 JDBC/SDK
 * 仓储的原子推进与会签」（持久化侧）；本测试覆盖「<b>报销这份定义</b>的形状与领域行为」。持久化已由 starter 收敛到 SDK @Sql （依赖元数据栈，H2
 * 裸测不可行），故本测试用内存端口替身驱动引擎领域逻辑——替代「另写一份看起来一样的 SQL」的假回归。
 */
class ExpenseWorkflowDefinitionTest {

  private static final String MIGRATION = "../../scripts/migration/0057_expense_workflow_seed.sql";
  private static final String DEF_KEY = "expense_reimbursement";
  private static final String ENTITY = "expense_reimbursement";

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
    assertThat(violations).as("0057 种子定义必须通过静态校验；违规即上线后 submit 时刻才炸的隐患").isEmpty();
  }

  @Test
  @DisplayName("档位阈值与节点/边结构与设计文档 §8.2 一致（改种子改错阈值即红）")
  void seedDefinition_structureMatchesSpec() {
    assertThat(definition.key()).isEqualTo(DEF_KEY);
    assertThat(definition.version()).isEqualTo(1);
    assertThat(definition.nodes()).hasSize(8);
    assertThat(definition.edges()).hasSize(10);

    // 三档 + 守卫的条件值（阈值漂移是种子最易错点）
    com.fasterxml.jackson.databind.JsonNode guard = edgeCondition("cond_amount_null", "finance");
    assertThat(guard.get("operator").asText()).isEqualTo("isNull");
    assertThat(guard.get("fieldCode").asText()).isEqualTo("amount");
    assertThat(edgeCondition("cond_high", "finance").get("value").decimalValue())
        .isEqualByComparingTo("5000");
    assertThat(edgeCondition("cond_mid", "dept").get("value").decimalValue())
        .isEqualByComparingTo("1000");

    // 默认边（else）必须末位：cond_amount_null / cond_high / cond_mid 各有一条无条件默认边
    for (String from : new String[] {"cond_amount_null", "cond_high", "cond_mid"}) {
      List<WorkflowDefinition.Edge> outs =
          definition.edges().stream().filter(e -> e.from().equals(from)).toList();
      assertThat(outs).hasSize(2);
      assertThat(outs.get(outs.size() - 1).when()).as("%s 的默认边必须末位", from).isNull();
    }

    // 审批节点恰一条无条件出边（审批节点不允许条件边）
    for (String node : new String[] {"mgr", "dept", "finance"}) {
      List<WorkflowDefinition.Edge> outs =
          definition.edges().stream().filter(e -> e.from().equals(node)).toList();
      assertThat(outs).hasSize(1);
      assertThat(outs.get(0).when()).isNull();
    }

    // 指派规则：三档都挂 FIELD approver_id；finance 追加 ROLE FINANCE_HEAD
    assertThat(definition.assignments().keySet())
        .containsExactlyInAnyOrder("mgr", "dept", "finance");
    assertThat(definition.assignments().get("finance")).hasSize(2);
    assertThat(definition.assignments().get("finance").get(1).strategy()).isEqualTo("ROLE");
    assertThat(definition.assignments().get("finance").get(1).params())
        .containsEntry("roleCode", "FINANCE_HEAD");
  }

  // ====================== 行为层：真引擎驱动这份定义 ======================

  @Test
  @DisplayName("amount=8000 > 5000 → finance 节点，办理人 = 表单 approver_id（三档分支·高档）")
  void highAmount_routesToFinance() {
    WorkflowInstance instance =
        start("r-high", Map.of("amount", 8000, "approver_id", "acc-finance"));

    assertThat(instance.currentNodeCode()).isEqualTo("finance");
    assertThat(engine.listPendingTasks(instance.id())).hasSize(1);
    assertThat(engine.listPendingTasks(instance.id()).get(0).assigneeId()).isEqualTo("acc-finance");
  }

  @Test
  @DisplayName("amount=3000（1000<x≤5000）→ dept 节点（中档）")
  void midAmount_routesToDept() {
    WorkflowInstance instance = start("r-mid", Map.of("amount", 3000, "approver_id", "acc-dept"));

    assertThat(instance.currentNodeCode()).isEqualTo("dept");
    assertThat(engine.listPendingTasks(instance.id()).get(0).assigneeId()).isEqualTo("acc-dept");
  }

  @Test
  @DisplayName("amount=800（≤1000）→ mgr 节点（低档）")
  void lowAmount_routesToMgr() {
    WorkflowInstance instance = start("r-low", Map.of("amount", 800, "approver_id", "acc-mgr"));

    assertThat(instance.currentNodeCode()).isEqualTo("mgr");
    assertThat(engine.listPendingTasks(instance.id()).get(0).assigneeId()).isEqualTo("acc-mgr");
  }

  @Test
  @DisplayName("amount 缺失 → isNull 守卫落 finance 最高档（fail-safe，绝不静默落最低档）")
  void nullAmount_routesToFinance() {
    WorkflowInstance instance = start("r-null", Map.of("approver_id", "acc-finance"));

    assertThat(instance.currentNodeCode()).isEqualTo("finance");
  }

  @Test
  @DisplayName("approver_id 缺失且组织目录未接入（FIELD+ROLE 双落空）→ 挂起，不放行、不静默跳过")
  void noAssignee_suspends() {
    WorkflowInstance instance = start("r-susp", Map.of("amount", 8000));

    assertThat(instance.isSuspended()).isTrue();
    assertThat(engine.listPendingTasks(instance.id())).isEmpty();
    // 挂起必须留痕（运维可见）且不落任何终态投影
    assertThat(statusCalls).anySatisfy(s -> assertThat(s.intent()).isEqualTo("SUSPENDED"));
    assertThat(statusCalls).noneSatisfy(s -> assertThat(s.intent()).isIn("APPROVED", "REJECTED"));
  }

  @Test
  @DisplayName("审批通过 → 实例 APPROVED；真实状态端口只留痕（action_code=null，人工动作同链路已落地 status）")
  void approve_reachesApproved_andPortRecordsOnly() {
    WorkflowInstance instance = start("r-approve", Map.of("amount", 800, "approver_id", "acc-mgr"));
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    WorkflowInstance after =
        engine.completeTask(task.id(), WorkflowTask.Decision.APPROVE, "同意", "acc-mgr");

    assertThat(after.phase()).isEqualTo(WorkflowInstance.Phase.APPROVED);
    assertThat(after.currentNodeCode()).isEqualTo("end");
    assertThat(engine.listPendingTasks(after.id())).isEmpty();
    assertThat(statusCalls).hasSize(1);
    assertThat(statusCalls.get(0).intent()).isEqualTo("APPROVED");
  }

  @Test
  @DisplayName("驳回 → 实例 REJECTED；驳回原因随意图留痕")
  void reject_reachesRejected_withReason() {
    WorkflowInstance instance = start("r-reject", Map.of("amount", 800, "approver_id", "acc-mgr"));
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);

    WorkflowInstance after =
        engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "缺发票", "acc-mgr");

    assertThat(after.phase()).isEqualTo(WorkflowInstance.Phase.REJECTED);
    assertThat(statusCalls).hasSize(1);
    assertThat(statusCalls.get(0).intent()).isEqualTo("REJECTED");
    assertThat(statusCalls.get(0).params()).isEqualTo("缺发票");
  }

  @Test
  @DisplayName("发起人撤回 → 实例 WITHDRAWN，活动槽位释放（允许改后重提 = 新实例）")
  void withdraw_reachesWithdrawn_andFreesSlot() {
    WorkflowInstance instance =
        start("r-withdraw", Map.of("amount", 800, "approver_id", "acc-mgr"));

    WorkflowInstance after = engine.withdraw(instance.id(), "u-applicant");

    assertThat(after.phase()).isEqualTo(WorkflowInstance.Phase.WITHDRAWN);
    WorkflowInstance resubmitted =
        start("r-withdraw", Map.of("amount", 800, "approver_id", "acc-mgr"));
    assertThat(resubmitted.id()).isNotEqualTo(instance.id());
    assertThat(resubmitted.phase()).isEqualTo(WorkflowInstance.Phase.RUNNING);
  }

  @Test
  @DisplayName("撤回后迟到审批 → INSTANCE_TERMINATED 拒绝（终态不可回写）")
  void lateApproval_afterWithdraw_isRejected() {
    WorkflowInstance instance = start("r-late", Map.of("amount", 800, "approver_id", "acc-mgr"));
    WorkflowTask task = engine.listPendingTasks(instance.id()).get(0);
    engine.withdraw(instance.id(), "u-applicant");

    assertThatThrownBy(
            () -> engine.completeTask(task.id(), WorkflowTask.Decision.APPROVE, "同意", "acc-mgr"))
        .isInstanceOf(WorkflowEngineException.class)
        .satisfies(
            ex ->
                assertThat(((WorkflowEngineException) ex).code())
                    .isEqualTo(WorkflowErrorCode.INSTANCE_TERMINATED));
  }

  @Test
  @DisplayName("重复 start（连点/重试）→ 复用既有实例，不产生双份审批")
  void repeatedStart_reusesInstance() {
    WorkflowInstance first = start("r-idem", Map.of("amount", 800, "approver_id", "acc-mgr"));
    WorkflowInstance second = start("r-idem", Map.of("amount", 800, "approver_id", "acc-mgr"));

    assertThat(second.id()).isEqualTo(first.id());
    assertThat(engine.listPendingTasks(first.id())).hasSize(1);
  }

  @Test
  @DisplayName("被驳回后重提（reopen→DRAFT→submit）→ 新实例走当前定义（版本快照语义）")
  void resubmitAfterReject_createsNewInstance() {
    WorkflowInstance first = start("r-reject2", Map.of("amount", 800, "approver_id", "acc-mgr"));
    WorkflowTask task = engine.listPendingTasks(first.id()).get(0);
    engine.completeTask(task.id(), WorkflowTask.Decision.REJECT, "缺发票", "acc-mgr");

    WorkflowInstance second = start("r-reject2", Map.of("amount", 800, "approver_id", "acc-mgr"));

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(second.phase()).isEqualTo(WorkflowInstance.Phase.RUNNING);
  }

  // ====================== 夹具 ======================

  private com.fasterxml.jackson.databind.JsonNode edgeCondition(String from, String to) {
    return definition.edges().stream()
        .filter(e -> e.from().equals(from) && e.to().equals(to))
        .findFirst()
        .orElseThrow()
        .when();
  }

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

  /**
   * 状态投影记录替身：断言「引擎在何时调用哪个投影方法、带什么参数」。
   *
   * <p>本端口自身的契约（只留痕不调动作 / action_code=null / 降级不抛异常）由 {@link ExpenseWorkflowStatusPortTest}
   * 单测覆盖——那里 mock 掉 outbox 仓储即可断言 insert 入参；本测试聚焦「这份定义驱动引擎」的领域行为。
   */
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

      // 接口新增（转办 / 加签协作指令，afc8e0282 引入）：本存根只验证定义与流程，审计留痕无需断言，故 no-op。
      @Override
      public void recordTransfer(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}

      @Override
      public void recordAddSign(
          WorkflowTask task, String newAssigneeId, String operatorId, String comment) {}
    };
  }

  /** 定义仓储替身：只返回从 0057 种子解析的定义。 */
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
   * 从 0057 迁移脚本解析 definition_json——<b>测试与上线种子同源</b>。
   *
   * <p>surefire 工作目录 = 模块基目录（bone-apps/bone-app-expense），故迁移脚本在两级之上。解析失败（文件被移动/JSON 行被
   * 重排）时测试直接失败——不允许静默跳过，否则「测试绿、上线种子坏」。
   */
  private WorkflowDefinition loadSeededDefinition() throws IOException {
    Path migration = Paths.get(MIGRATION);
    assertThat(Files.exists(migration))
        .as("0057 迁移脚本必须存在（测试与上线种子同源的锚点）: %s", migration.toAbsolutePath())
        .isTrue();
    String json = null;
    for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
      if (line.contains("'{\"key\":\"expense_reimbursement\"")) {
        json = line.substring(line.indexOf("'{") + 1, line.lastIndexOf("}'") + 1);
        break;
      }
    }
    assertThat(json).as("0057 中必须能解析出 expense_reimbursement 的定义 JSON").isNotNull();
    try {
      return mapper.readValue(json, WorkflowDefinition.class);
    } catch (Exception ex) {
      throw new IllegalStateException("0057 种子 JSON 无法反序列化为 WorkflowDefinition（形状错误）", ex);
    }
  }

  /**
   * 内存端口替身：实例/任务/活动槽位/原子推进的内存实现。
   *
   * <p>复刻生产仓储的并发口径（CAS / saveTaskIfAbsent / claimActiveInstance 陈旧指针清理 / compareAndAdvanceNode
   * 完成条件）， 但以内存 Map 表达——<b>生产原子 SQL 的正确性由 starter 自带 H2
   * 回归覆盖</b>（JdbcWorkflowInstanceRepositoryH2Test 等）， 本替身的职责是让「报销这份定义」的领域行为可被断言，而不是重造一套持久化。
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
          activeSlots.remove(key); // 陈旧指针：上一次流程已终态，清理后重试一次
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
      // 内存简化：测试桩不区分轮次，直接返回该节点全部任务（报销定义单轮，无多轮往返场景）。
      return listTasksByNode(instanceId, nodeCode);
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
      // 实例终态守卫（与生产 SQL 的 EXISTS phase='RUNNING' 同语义）
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
              Instant.now()));
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
      if (mode == null) { // AUTO：非审批节点无条件移动
        satisfied = true;
      } else if (mode == WorkflowDefinition.ApprovalMode.ALL) {
        // 全员完成（quorum 为 null/≤0 时，向后兼容）；quorum 给定时至少 quorum 个任务完成才推进
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
        // 比例会签：已通过占比 ≥ quorum（quorum 视作百分比阈值 0–100）
        long approved =
            nodeTasks.stream().filter(t -> t.status() == WorkflowTask.Status.APPROVED).count();
        satisfied =
            !nodeTasks.isEmpty()
                && quorum != null
                && quorum > 0
                && approved * 100 >= quorum * nodeTasks.size();
      } else { // SINGLE / ANY
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
              Instant.now()));
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
              Instant.now()));
      if (next != WorkflowInstance.Phase.RUNNING && next != WorkflowInstance.Phase.SUSPENDED) {
        releaseActiveInstance(instance.entityCode(), instance.recordId());
      }
      return true;
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
      cancel(instanceId, null);
    }

    @Override
    public void cancelPendingTasksOfNode(String instanceId, String nodeCode, String operatorId) {
      cancel(instanceId, nodeCode);
    }

    private void cancel(String instanceId, String nodeCode) {
      List<WorkflowTask> pending = new ArrayList<>(listPendingTasks(instanceId));
      for (WorkflowTask old : pending) {
        if (nodeCode != null && !old.nodeCode().equals(nodeCode)) {
          continue;
        }
        tasks.remove(old);
        tasks.add(
            new WorkflowTask(
                old.id(),
                old.instanceId(),
                old.nodeCode(),
                old.assigneeId(),
                WorkflowTask.Status.CANCELLED,
                old.decision(),
                old.comment(),
                old.dueAt(),
                old.createdAt(),
                Instant.now()));
      }
    }
  }
}
